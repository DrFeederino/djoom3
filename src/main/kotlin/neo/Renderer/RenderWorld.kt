package neo.Renderer

import neo.Renderer.Material.idMaterial
import neo.Renderer.Model.idRenderModel
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Sound.sound.idSoundEmitter
import neo.TempDump
import neo.TempDump.Atomics.*
import neo.TempDump.SERiAL
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.DeclManager
import neo.framework.DeclSkin.idDeclSkin
import neo.framework.DemoFile.idDemoFile
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.Lib.idException
import neo.idlib.Lib.idLib
import neo.idlib.containers.CInt
import neo.idlib.geometry.JointTransform.idJointMat
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.*
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.ui.UserInterface.idUserInterface
import java.nio.*
import java.util.*

class RenderWorld {
    /*
     ===============================================================================

     Render World

     ===============================================================================
     */

    companion object {

        // shader parms
        const val MAX_GLOBAL_SHADER_PARMS = 12

        // guis
        const val MAX_RENDERENTITY_GUI = 3
        const val SHADERPARM_ALPHA = 3

        //
        const val PROC_FILE_EXT: String = "proc"
        const val PROC_FILE_ID: String = "mapProcFile003"

        //
        const val SHADERPARM_BEAM_END_X = 8 // for _beam models
        const val SHADERPARM_BEAM_END_Y = 9
        const val SHADERPARM_BEAM_END_Z = 10
        const val SHADERPARM_BEAM_WIDTH = 11
        const val SHADERPARM_BLUE = 2
        const val SHADERPARM_DIVERSITY = 5 // random between 0.0 and 1.0 for some effects (muzzle flashes, etc)
        const val SHADERPARM_GREEN = 1
        const val SHADERPARM_MD3_BACKLERP = 10

        //
        const val SHADERPARM_MD3_FRAME = 8
        const val SHADERPARM_MD3_LASTFRAME = 9

        // model parms
        const val SHADERPARM_MD5_SKINSCALE = 8 // for scaling vertex offsets on md5 models (jack skellington effect)
        const val SHADERPARM_MODE = 7 // for selecting which shader passes to enable

        //
        const val SHADERPARM_PARTICLE_STOPTIME = 8 // don't spawn any more particles after this time

        //
        const val SHADERPARM_RED = 0
        const val SHADERPARM_SPRITE_HEIGHT = 9

        //
        const val SHADERPARM_SPRITE_WIDTH = 8
        const val SHADERPARM_TIMEOFFSET = 4
        const val SHADERPARM_TIMESCALE = 3
        const val SHADERPARM_TIME_OF_DEATH = 7 // for the monster skin-burn-away effect enable and time offset

        //
        const val NUM_PORTAL_ATTRIBUTES = 3 //PS_BLOCK_ALL needs to be changed manually if this value is changed.

    }

    /*
     ===============
     R_GlobalShaderOverride
     ===============
     */
    @Throws(idException::class)
    fun R_GlobalShaderOverride(shader: Array<idMaterial>): Boolean {
        if (!shader.get(0).IsDrawn()) {
            return false
        }
        if (tr_local.tr.primaryRenderView.globalMaterial != null) {
            shader.get(0) = tr_local.tr.primaryRenderView.globalMaterial
            return true
        }
        if (TempDump.isNotNullOrEmpty(RenderSystem_init.r_materialOverride.GetString())) {
            shader.get(0) = DeclManager.declManager.FindMaterial(RenderSystem_init.r_materialOverride.GetString())
            return true
        }
        return false
    }

    /*
     ===============
     R_RemapShaderBySkin
     ===============
     */
    fun R_RemapShaderBySkin(shader: idMaterial, skin: idDeclSkin?, customShader: idMaterial): idMaterial? {
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
            return if (shader.Deform() != null) {
                null
            } else customShader
        }
        return if (null == skin) {
            shader
        } else skin.RemapShaderBySkin(shader)
    }

    enum class portalConnection_t {
        PS_BLOCK_NONE,  // = 0,

        //
        PS_BLOCK_VIEW,  // = 1,
        PS_BLOCK_LOCATION,  // = 2,  // game map location strings often stop in hallways

        /**
         * padding
         */
        __3, PS_BLOCK_AIR,  // = 4,       // windows between pressurized and unpresurized areas
        //
        /**
         * padding
         */
        __5,

        /**
         * padding
         */
        __6, PS_BLOCK_ALL //= (1 << NUM_PORTAL_ATTRIBUTES) - 1
    }

    abstract class deferredEntityCallback_t : SERiAL {
        abstract fun run(e: renderEntity_s?, v: renderView_s?): Boolean
    }

    class renderEntity_s {
        val shaderParms: FloatArray? =
            FloatArray(Material.MAX_ENTITY_SHADER_PARMS) // can be used in any way by shader or model generation
        private val DBG_count = DBG_counter++

        //
        // if non-zero, the surface and shadow (if it casts one)
        // will only show up in the specific view, ie: player weapons
        var allowSurfaceInViewID = 0
        var axis: idMat3?
        var bodyId = 0

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
                : idBounds?
        var callback: deferredEntityCallback_t? = null

        //
        var callbackData // used for whatever the callback wants
                : ByteBuffer? = null

        //
        // texturing
        var customShader // if non-0, all surfaces will use this
                : idMaterial? = null
        var customSkin // 0 for no remappings
                : idDeclSkin? = null

        //
        var entityNum = 0

        // this automatically implies noShadow
        var forceUpdate // force an update (NOTE: not a bool to keep this struct a multiple of 4 bytes)//TODO:
                = 0

        // networking: see WriteGUIToSnapshot / ReadGUIFromSnapshot
        var gui: Array<idUserInterface?>? = arrayOfNulls<idUserInterface?>(RenderWorld.MAX_RENDERENTITY_GUI)
        var hModel // this can only be null if callback is set
                : idRenderModel? = null
        var joints // array of joints that will modify vertices.
                : Array<idJointMat?>?

        // NULL if non-deformable model.  NOT freed by renderer
        //
        var modelDepthHack // squash depth range so particle effects don't clip into walls
                = 0f

        //
        var noDynamicInteractions // don't create any light / shadow interactions after
                = false

        //
        // options to override surface shader flags (replace with material parameters?)
        var noSelfShadow // cast shadows onto other objects,but not self
                = false
        var noShadow // no shadow at all
                = false

        //
        var numJoints = 0

        //
        // positioning
        // axis rotation vectors must be unit length for many
        // R_LocalToGlobal functions to work, so don't scale models!
        // axis vectors are [0] = forward, [1] = left, [2] = up
        val origin: idVec3?
        var referenceShader // used so flares can reference the proper light shader
                : idMaterial? = null
        var referenceSound // for shader sound tables, allowing effects to vary with sounds
                : idSoundEmitter? = null

        //
        var remoteRenderView // any remote camera surfaces will use this
                : renderView_s? = null

        //
        // world models for the player and weapons will not cast shadows from view weapon
        // muzzle flashes
        var suppressShadowInLightID = 0
        var suppressShadowInViewID = 0

        //
        // player bodies and possibly player shadows should be suppressed in views from
        // that player's eyes, but will show up in mirrors and other subviews
        // security cameras could suppress their model in their subviews if we add a way
        // of specifying a view number for a remoteRenderMap view
        var suppressSurfaceInViewID = 0
        var timeGroup = 0

        // the level load is completed.  This is a performance hack
        // for the gigantic outdoor meshes in the monorail map, so
        // all the lights in the moving monorail don't touch the meshes
        //
        var weaponDepthHack // squash depth range so view weapons don't poke into walls
                = false
        var xrayIndex = 0

        constructor() {
            origin = idVec3()
            axis = idMat3()
            bounds = idBounds()
        }

        constructor(newEntity: renderEntity_s?) {
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
                gui.get(i) = newEntity.gui.get(i)
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

        fun atomicSet(shadow: renderEntityShadow?) {
            hModel = shadow.hModel
            entityNum = shadow.entityNum.getVal()
            bodyId = shadow.bodyId.getVal()
            bounds = shadow.bounds
            callback = shadow.callback
            callbackData = shadow.callbackData
            suppressSurfaceInViewID = shadow.suppressSurfaceInViewID.getVal()
            suppressShadowInViewID = shadow.suppressShadowInViewID.getVal()
            suppressShadowInLightID = shadow.suppressShadowInLightID.getVal()
            allowSurfaceInViewID = shadow.allowSurfaceInViewID.getVal()
            origin.oSet(shadow.origin)
            axis = shadow.axis
            customShader = shadow.customShader
            referenceShader = shadow.referenceShader
            customSkin = shadow.customSkin
            referenceSound = shadow.referenceSound
            remoteRenderView = shadow.remoteRenderView
            numJoints = shadow.numJoints.getVal()
            joints = shadow.joints
            modelDepthHack = shadow.modelDepthHack.getVal()
            noSelfShadow = shadow.noSelfShadow.isVal
            noShadow = shadow.noShadow.isVal
            noDynamicInteractions = shadow.noDynamicInteractions.isVal
            weaponDepthHack = shadow.weaponDepthHack.isVal
            forceUpdate = shadow.forceUpdate.getVal()
            timeGroup = shadow.timeGroup.getVal()
            xrayIndex = shadow.xrayIndex.getVal()
        }

        fun clear() {
            val newEntity = renderEntity_s()
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
            origin.oSet(newEntity.origin)
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

        override fun hashCode(): Int {
            var hash = 7
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
            hash = 71 * hash + Arrays.hashCode(shaderParms)
            hash = 71 * hash + Arrays.deepHashCode(gui)
            hash = 71 * hash + Objects.hashCode(remoteRenderView)
            hash = 71 * hash + numJoints
            hash = 71 * hash + Arrays.deepHashCode(joints)
            hash = 71 * hash + java.lang.Float.floatToIntBits(modelDepthHack)
            hash = 71 * hash + if (noSelfShadow) 1 else 0
            hash = 71 * hash + if (noShadow) 1 else 0
            hash = 71 * hash + if (noDynamicInteractions) 1 else 0
            hash = 71 * hash + if (weaponDepthHack) 1 else 0
            hash = 71 * hash + forceUpdate
            hash = 71 * hash + timeGroup
            hash = 71 * hash + xrayIndex
            return hash
        }

        override fun equals(obj: Any?): Boolean {
            if (obj == null) {
                return false
            }
            if (javaClass != obj.javaClass) {
                return false
            }
            val other = obj as renderEntity_s?
            if (hModel != other.hModel) {
                return false
            }
            if (entityNum != other.entityNum) {
                return false
            }
            if (bodyId != other.bodyId) {
                return false
            }
            if (bounds != other.bounds) {
                return false
            }
            if (callback != other.callback) {
                return false
            }
            if (callbackData != other.callbackData) {
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
            if (origin != other.origin) {
                return false
            }
            if (axis != other.axis) {
                return false
            }
            if (customShader != other.customShader) {
                return false
            }
            if (referenceShader != other.referenceShader) {
                return false
            }
            if (customSkin != other.customSkin) {
                return false
            }
            if (referenceSound != other.referenceSound) {
                return false
            }
            if (!Arrays.equals(shaderParms, other.shaderParms)) {
                return false
            }
            if (!Arrays.deepEquals(gui, other.gui)) {
                return false
            }
            if (remoteRenderView != other.remoteRenderView) {
                return false
            }
            if (numJoints != other.numJoints) {
                return false
            }
            if (!Arrays.deepEquals(joints, other.joints)) {
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
            return if (timeGroup != other.timeGroup) {
                false
            } else xrayIndex == other.xrayIndex
        }

        companion object {
            private var DBG_counter = 0
        }
    }

    class renderLight_s {
        val end: idVec3? = idVec3()
        val lightCenter: idVec3? = idVec3() // offset the lighting direction for shading and
        val lightRadius: idVec3? = idVec3() // xyz radius for point lights
        val origin: idVec3? = idVec3()
        val right: idVec3? = idVec3()
        val shaderParms: FloatArray? = FloatArray(Material.MAX_ENTITY_SHADER_PARMS) // can be used in any way by shader
        val start: idVec3? = idVec3()

        // shadows, relative to origin
        //
        // frustum definition for projected lights, all reletive to origin
        // FIXME: we should probably have real plane equations here, and offer
        // a helper function for conversion from this format
        val target: idVec3? = idVec3()
        val up: idVec3? = idVec3()

        //
        // if non-zero, the light will only show up in the specific view
        // which can allow player gun gui lights and such to not effect everyone
        var allowLightInViewID = 0
        var axis: idMat3? = idMat3() // rotation vectors, must be unit length

        //
        // muzzle flash lights will not cast shadows from player and weapon world models
        var lightId = 0

        //
        // I am sticking the four bools together so there are no unused gaps in
        // the padded structure, which could confuse the memcmp that checks for redundant
        // updates
        var noShadows // (should we replace this with material parameters on the shader?)
                = false
        var noSpecular // (should we replace this with material parameters on the shader?)
                = false
        var parallel // lightCenter gives the direction to the light at infinity
                = false

        //
        var pointLight // otherwise a projection light (should probably invert the sense of this, because points are way more common)
                = false

        //
        // Dmap will generate an optimized shadow volume named _prelight_<lightName>
        // for the light against all the _area* models in the map.  The renderer will
        // ignore this value if the light has been moved after initial creation
        var prelightModel: idRenderModel? = null
        var referenceSound // for shader sound tables, allowing effects to vary with sounds
                : idSoundEmitter? = null

        //
        //
        var shader // NULL = either lights/defaultPointLight or lights/defaultProjectedLight
                : idMaterial? = null

        //
        // if non-zero, the light will not show up in the specific view,
        // which may be used if we want to have slightly different muzzle
        // flash lights for the player and other views
        var suppressLightInViewID = 0

        constructor()

        //copy constructor
        constructor(other: renderLight_s?) {
            axis = idMat3(other.axis)
            origin.oSet(other.origin)
            suppressLightInViewID = other.suppressLightInViewID
            allowLightInViewID = other.allowLightInViewID
            noShadows = other.noShadows
            noSpecular = other.noSpecular
            pointLight = other.pointLight
            parallel = other.parallel
            lightRadius.oSet(other.lightRadius)
            lightCenter.oSet(other.lightCenter)
            target.oSet(other.target)
            right.oSet(other.right)
            up.oSet(other.up)
            start.oSet(other.start)
            end.oSet(other.end)
            prelightModel = other.prelightModel
            lightId = other.lightId
            shader = other.shader
            System.arraycopy(other.shaderParms, 0, shaderParms, 0, other.shaderParms.size)
            referenceSound = other.referenceSound
        }

        fun clear() { //TODO:hardcoded values
            val temp = renderLight_s()
            axis = temp.axis
            origin.oSet(temp.origin)
            suppressLightInViewID = temp.suppressLightInViewID
            allowLightInViewID = temp.allowLightInViewID
            noShadows = temp.noShadows
            noSpecular = temp.noSpecular
            pointLight = temp.pointLight
            parallel = temp.parallel
            lightRadius.oSet(temp.lightRadius)
            lightCenter.oSet(temp.lightCenter)
            target.oSet(temp.target)
            right.oSet(temp.right)
            up.oSet(temp.up)
            start.oSet(temp.start)
            end.oSet(temp.end)
            prelightModel = temp.prelightModel
            lightId = temp.lightId
            shader = temp.shader
            referenceSound = temp.referenceSound
        }

        fun atomicSet(shadow: renderLightShadow?) {
            axis = shadow.axis
            origin.oSet(shadow.origin)
            suppressLightInViewID = shadow.suppressLightInViewID.getVal()
            allowLightInViewID = shadow.allowLightInViewID.getVal()
            noShadows = shadow.noShadows.isVal
            noSpecular = shadow.noSpecular.isVal
            pointLight = shadow.pointLight.isVal
            parallel = shadow.parallel.isVal
            lightRadius.oSet(shadow.lightRadius)
            lightCenter.oSet(shadow.lightCenter)
            target.oSet(shadow.target)
            right.oSet(shadow.right)
            up.oSet(shadow.up)
            start.oSet(shadow.start)
            end.oSet(shadow.end)
            prelightModel = shadow.prelightModel
            lightId = shadow.lightId.getVal()
            shader = shadow.shader
            referenceSound = shadow.referenceSound
        }
    }

    class renderView_s : SERiAL {
        val vieworg: idVec3? = idVec3()
        private val DBG_count = DBG_counter++

        //
        var cramZNear // for cinematics, we want to set ZNear much lower
                = false
        var forceUpdate // for an update
                = false

        //
        var fov_x = 0f
        var fov_y = 0f
        var globalMaterial // used to override everything draw
                : idMaterial? = null
        var shaderParms: FloatArray? =
            FloatArray(RenderWorld.MAX_GLOBAL_SHADER_PARMS) // can be used in any way by shader

        //
        // time in milliseconds for shader effects and other time dependent rendering issues
        var time = 0
        var viewID = 0
        var viewaxis: idMat3? = idMat3() // transformation matrix, view looks down the positive X axis

        //
        // sized from 0 to SCREEN_WIDTH / SCREEN_HEIGHT (640/480), not actual resolution
        var x = 0
        var y = 0
        var width = 0
        var height = 0

        constructor()
        constructor(renderView: renderView_s?) {
            viewID = renderView.viewID
            x = renderView.x
            y = renderView.y
            width = renderView.width
            height = renderView.height
            fov_x = renderView.fov_x
            fov_y = renderView.fov_y
            vieworg.oSet(renderView.vieworg)
            viewaxis = idMat3(renderView.viewaxis)
            cramZNear = renderView.cramZNear
            forceUpdate = renderView.forceUpdate
            time = renderView.time
            globalMaterial = renderView.globalMaterial
        }

        fun atomicSet(shadow: renderViewShadow?) {
            viewID = shadow.viewID.getVal()
            x = shadow.x.getVal()
            y = shadow.y.getVal()
            width = shadow.width.getVal()
            height = shadow.height.getVal()
            fov_x = shadow.fov_x.getVal()
            fov_y = shadow.fov_y.getVal()
            vieworg.oSet(shadow.vieworg)
            viewaxis = idMat3(shadow.viewaxis)
            cramZNear = shadow.cramZNear.isVal
            forceUpdate = shadow.forceUpdate.isVal
            time = shadow.time.getVal()
            for (a in 0 until RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                shaderParms.get(a) = shadow.shaderParms[a].getVal()
            }
            globalMaterial = shadow.globalMaterial
        }

        override fun AllocBuffer(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Read(buffer: ByteBuffer?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        override fun Write(): ByteBuffer? {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        companion object {
            // player views will set this to a non-zero integer for model suppress / allow
            // subviews (mirrors, cameras, etc) will always clear it to zero
            private var DBG_counter = 0
        }
    }

    // exitPortal_t is returned by idRenderWorld::GetPortal()
    class exitPortal_t {
        var areas: IntArray? = IntArray(2) // areas connected by this portal
        var blockingBits // PS_BLOCK_VIEW, PS_BLOCK_AIR, etc
                = 0
        var   /*qhandle_t */portalHandle = 0
        var w // winding points have counter clockwise ordering seen from areas[0]
                : idWinding? = null
    }

    // guiPoint_t is returned by idRenderWorld::GuiTrace()
    class guiPoint_t {
        var guiId // id of gui ( 0, 1, or 2 ) that the trace happened against
                = 0
        var x = 0f
        var y // 0.0 to 1.0 range if trace hit a gui, otherwise -1
                = 0f
    }

    // modelTrace_t is for tracing vs. visual geometry
    class modelTrace_s {
        var entity // render entity that was hit
                : renderEntity_s? = null
        var fraction // fraction of trace completed
                = 0f
        var jointNumber // md5 joint nearest to the hit triangle
                = 0
        var material // material of hit surface
                : idMaterial? = null
        val normal: idVec3? = idVec3() // hit triangle normal vector in global space
        val point: idVec3? = idVec3() // end point of trace in global space
        fun clear() {
            point.Zero()
            normal.Zero()
            material = idMaterial()
            entity = renderEntity_s()
            jointNumber = 0
            fraction = jointNumber.toFloat()
        }
    }

    abstract class idRenderWorld {
        //	virtual					~idRenderWorld() {};
        // The same render world can be reinitialized as often as desired
        // a NULL or empty mapName will create an empty, single area world
        @Throws(idException::class)
        abstract fun InitFromMap(mapName: String?): Boolean

        //-------------- Entity and Light Defs -----------------
        // entityDefs and lightDefs are added to a given world to determine
        // what will be drawn for a rendered scene.  Most update work is defered
        // until it is determined that it is actually needed for a given view.
        abstract fun AddEntityDef(re: renderEntity_s?): Int
        abstract fun UpdateEntityDef(entityHandle: Int, re: renderEntity_s?)
        abstract fun FreeEntityDef(entityHandle: Int)
        abstract fun GetRenderEntity(entityHandle: Int): renderEntity_s?
        abstract fun AddLightDef(rlight: renderLight_s?): Int
        abstract fun UpdateLightDef(lightHandle: Int, rlight: renderLight_s?)
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
            winding: idFixedWinding?,
            projectionOrigin: idVec3?,
            parallel: Boolean,
            fadeDepth: Float,
            material: idMaterial?,
            startTime: Int
        )

        // Creates decals on static models.
        abstract fun ProjectDecal(
            entityHandle: Int,
            winding: idFixedWinding?,
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
        abstract fun RenderScene(renderView: renderView_s?)

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
        abstract fun AreasAreConnected(areaNum1: Int, areaNum2: Int, connection: portalConnection_t?): Boolean

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
        abstract fun GetPortal(areaNum: Int, portalNum: Int): exitPortal_t?

        //-------------- Tracing  -----------------
        // Checks a ray trace against any gui surfaces in an entity, returning the
        // fraction location of the trace on the gui surface, or -1,-1 if no hit.
        // This doesn't do any occlusion testing, simply ignoring non-gui surfaces.
        // start / end are in global world coordinates.
        abstract fun GuiTrace(entityHandle: Int, start: idVec3?, end: idVec3?): guiPoint_t?

        // Traces vs the render model, possibly instantiating a dynamic version, and returns true if something was hit
        abstract fun ModelTrace(
            trace: modelTrace_s?,
            entityHandle: Int,
            start: idVec3?,
            end: idVec3?,
            radius: Float
        ): Boolean

        // Traces vs the whole rendered world. FIXME: we need some kind of material flags.
        abstract fun Trace(
            trace: modelTrace_s?,
            start: idVec3?,
            end: idVec3?,
            radius: Float,
            skipDynamic: Boolean /*= true*/,
            skipPlayer: Boolean /* = false*/
        ): Boolean

        @JvmOverloads
        fun Trace(
            trace: modelTrace_s?,
            start: idVec3?,
            end: idVec3?,
            radius: Float,
            skipDynamic: Boolean = true
        ): Boolean {
            return Trace(trace, start, end, radius, skipDynamic, false)
        }

        // Traces vs the world model bsp tree.
        abstract fun FastWorldTrace(trace: modelTrace_s?, start: idVec3?, end: idVec3?): Boolean

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
            demoRenderView: renderView_s?,
            demoTimeOffset: CInt?
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

        abstract fun DebugArrow(color: idVec4?, start: idVec3?, end: idVec3?, size: Int, lifetime: Int /*= 0*/)
        fun DebugArrow(color: idVec4?, start: idVec3?, end: idVec3?, size: Int) {
            DebugArrow(color, start, end, size, 0)
        }

        abstract fun DebugWinding(
            color: idVec4?,
            w: idWinding?,
            origin: idVec3?,
            axis: idMat3?,
            lifetime: Int /*= 0*/,
            depthTest: Boolean /*= false*/
        )

        @JvmOverloads
        fun DebugWinding(color: idVec4?, w: idWinding?, origin: idVec3?, axis: idMat3?, lifetime: Int = 0 /*= 0*/) {
            DebugWinding(color, w, origin, axis, lifetime, false)
        }

        abstract fun DebugCircle(
            color: idVec4?,
            origin: idVec3?,
            dir: idVec3?,
            radius: Float,
            numSteps: Int,
            lifetime: Int /* = 0*/,
            depthTest: Boolean /*= false */
        )

        @JvmOverloads
        fun DebugCircle(
            color: idVec4?,
            origin: idVec3?,
            dir: idVec3?,
            radius: Float,
            numSteps: Int,
            lifetime: Int = 0 /* = 0*/
        ) {
            DebugCircle(color, origin, dir, radius, numSteps, lifetime, false)
        }

        abstract fun DebugSphere(
            color: idVec4?,
            sphere: idSphere?,
            lifetime: Int /* = 0*/,
            depthTest: Boolean /* = false */
        )

        @JvmOverloads
        fun DebugSphere(color: idVec4?, sphere: idSphere?, lifetime: Int = 0 /* = 0*/) {
            DebugSphere(color, sphere, lifetime, false)
        }

        abstract fun DebugBounds(
            color: idVec4?,
            bounds: idBounds?,
            org: idVec3? /* = vec3_origin*/,
            lifetime: Int /* = 0*/
        )

        @JvmOverloads
        fun DebugBounds(color: idVec4?, bounds: idBounds?, org: idVec3? = Vector.getVec3_origin() /* = vec3_origin*/) {
            DebugBounds(color, bounds, org, 0)
        }

        abstract fun DebugBox(color: idVec4?, box: idBox?, lifetime: Int /* = 0*/)
        fun DebugBox(color: idVec4?, box: idBox?) {
            DebugBox(color, box, 0)
        }

        abstract fun DebugFrustum(
            color: idVec4?,
            frustum: idFrustum?,
            showFromOrigin: Boolean /* = false*/,
            lifetime: Int /*= 0*/
        )

        @JvmOverloads
        fun DebugFrustum(color: idVec4?, frustum: idFrustum?, showFromOrigin: Boolean = false /* = false*/) {
            DebugFrustum(color, frustum, showFromOrigin, 0)
        }

        abstract fun DebugCone(
            color: idVec4?,
            apex: idVec3?,
            dir: idVec3?,
            radius1: Float,
            radius2: Float,
            lifetime: Int /*= 0*/
        )

        fun DebugCone(color: idVec4?, apex: idVec3?, dir: idVec3?, radius1: Float, radius2: Float) {
            DebugCone(color, apex, dir, radius1, radius2, 0)
        }

        abstract fun DebugAxis(origin: idVec3?, axis: idMat3?)

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
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var ldef: idRenderLightLocal?
            if (null == tr_local.tr.primaryWorld) {
                return
            }
            var active = 0
            var totalRef = 0
            var totalIntr = 0
            i = 0
            while (i < tr_local.tr.primaryWorld.lightDefs.Num()) {
                ldef = tr_local.tr.primaryWorld.lightDefs.oGet(i)
                if (null == ldef) {
                    idLib.common.Printf("%4d: FREED\n", i)
                    i++
                    continue
                }

                // count up the interactions
                var iCount = 0
                var inter = ldef.firstInteraction
                while (inter != null) {
                    iCount++
                    inter = inter.lightNext
                }
                totalIntr += iCount

                // count up the references
                var rCount = 0
                var ref = ldef.references
                while (ref != null) {
                    rCount++
                    ref = ref.ownerNext
                }
                totalRef += rCount
                idLib.common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, ldef.lightShader.GetName())
                active++
                i++
            }
            idLib.common.Printf("%d lightDefs, %d interactions, %d areaRefs\n", active, totalIntr, totalRef)
        }

        companion object {
            private val instance: cmdFunction_t? = R_ListRenderLightDefs_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ===================
     R_ListRenderEntityDefs_f
     ===================
     */
    class R_ListRenderEntityDefs_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            var i: Int
            var mdef: idRenderEntityLocal?
            if (null == tr_local.tr.primaryWorld) {
                return
            }
            var active = 0
            var totalRef = 0
            var totalIntr = 0
            i = 0
            while (i < tr_local.tr.primaryWorld.entityDefs.Num()) {
                mdef = tr_local.tr.primaryWorld.entityDefs.oGet(i)
                if (null == mdef) {
                    idLib.common.Printf("%4d: FREED\n", i)
                    i++
                    continue
                }

                // count up the interactions
                var iCount = 0
                var inter = mdef.firstInteraction
                while (inter != null) {
                    iCount++
                    inter = inter.entityNext
                }
                totalIntr += iCount

                // count up the references
                var rCount = 0
                var ref = mdef.entityRefs
                while (ref != null) {
                    rCount++
                    ref = ref.ownerNext
                }
                totalRef += rCount
                idLib.common.Printf("%4d: %3d intr %2d refs %s\n", i, iCount, rCount, mdef.parms.hModel.Name())
                active++
                i++
            }
            idLib.common.Printf("total active: %d\n", active)
        }

        companion object {
            private val instance: cmdFunction_t? = R_ListRenderEntityDefs_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }
}