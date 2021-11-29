package neo.Renderer

import neo.Renderer.Interaction.areaNumRef_s
import neo.Renderer.Interaction.idInteraction
import neo.Renderer.Material.materialCoverage_t
import neo.Renderer.Material.shaderStage_t
import neo.Renderer.Model.dynamicModel_t
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.shadowCache_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelDecal.decalProjectionInfo_s
import neo.Renderer.ModelDecal.idRenderModelDecal
import neo.Renderer.ModelOverlay.idRenderModelOverlay
import neo.Renderer.RenderWorld.*
import neo.Renderer.RenderWorld_demo.demoHeader_t
import neo.Renderer.RenderWorld_portals.portalStack_s
import neo.Renderer.tr_lightrun.R_RegenerateWorld_f
import neo.Renderer.tr_local.areaReference_s
import neo.Renderer.tr_local.demoCommand_t
import neo.Renderer.tr_local.idRenderEntityLocal
import neo.Renderer.tr_local.idRenderLightLocal
import neo.Renderer.tr_local.idScreenRect
import neo.Renderer.tr_local.localTrace_t
import neo.Renderer.tr_local.viewDef_s
import neo.Renderer.tr_local.viewEntity_s
import neo.Renderer.tr_local.viewLight_s
import neo.TempDump
import neo.TempDump.Atomics.*
import neo.TempDump.CPP_class.Char
import neo.framework.*
import neo.framework.DemoFile.demoSystem_t
import neo.framework.DemoFile.idDemoFile
import neo.idlib.*
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BV.Box.idBox
import neo.idlib.BV.Frustum.idFrustum
import neo.idlib.BV.Sphere.idSphere
import neo.idlib.Lib.idException
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import neo.sys.win_shared
import neo.ui.UserInterface
import org.lwjgl.opengl.GL11
import java.nio.*
import java.util.*
import java.util.stream.Stream

/**
 *
 */
object RenderWorld_local {
    const val AREANUM_SOLID = -1
    const val CHILDREN_HAVE_MULTIPLE_AREAS = -2

    // assume any lightDef or entityDef index above this is an internal error
    const val LUDICROUS_INDEX = 10000

    //
    const val WRITE_GUIS = false

    class portal_s {
        var doublePortal: doublePortal_s? = null
        var intoArea // area this portal leads to
                = 0
        var next // next portal of the area
                : portal_s? = null
        val plane: idPlane? = idPlane() // view must be on the positive side of the plane to cross
        var w // winding points have counter clockwise ordering seen this area
                : idWinding?

        init {
            w = idWinding()
        }
    }

    class doublePortal_s {
        var blockingBits // PS_BLOCK_VIEW, PS_BLOCK_AIR, etc, set by doors that shut them off
                = 0

        //
        // A portal will be considered closed if it is past the
        // fog-out point in a fog volume.  We only support a single
        // fog volume over each portal.
        var fogLight: idRenderLightLocal? = null
        var nextFoggedPortal: doublePortal_s? = null
        var portals: Array<portal_s?>? = arrayOfNulls<portal_s?>(2)
    }

    class portalArea_s {
        var areaNum = 0
        var connectedAreaNum // if two areas have matching connectedAreaNum, they are
                : IntArray?
        var entityRefs // head/tail of doubly linked list, may change
                : areaReference_s?
        var lightRefs // head/tail of doubly linked list, may change
                : areaReference_s?
        var portals // never changes after load
                : portal_s? = null

        // not separated by a portal with the apropriate PS_BLOCK_* blockingBits
        var viewCount // set by R_FindViewLightsAndEntities
                = 0

        companion object {
            fun generateArray(length: Int): Array<portalArea_s?>? {
                return Stream.generate { portalArea_s() }.limit(length.toLong()).toArray { _Dummy_.__Array__() }
            }
        }

        init {
            connectedAreaNum = IntArray(RenderWorld.NUM_PORTAL_ATTRIBUTES)
            entityRefs = areaReference_s()
            lightRefs = areaReference_s()
        }
    }

    class areaNode_t {
        var children: IntArray? = IntArray(2) // negative numbers are (-1 - areaNumber), 0 = solid
        var commonChildrenArea // if all children are either solid or a single area,
                = 0
        val plane: idPlane? =
            idPlane() //                              // this is the area number, else CHILDREN_HAVE_MULTIPLE_AREAS
    }

    class idRenderWorldLocal : idRenderWorld() {
        //
        var areaNodes: Array<areaNode_t?>?

        //
        var areaScreenRect: Array<idScreenRect?>?
        var connectedAreaNum // incremented every time a door portal state changes
                = 0

        //
        var doublePortals: Array<doublePortal_s?>?

        //
        val entityDefs: idList<idRenderEntityLocal?>? = idList()

        //
        //
        var generateAllInteractionsCalled: Boolean

        //
        //        public final idBlockAlloc<areaReference_s> areaReferenceAllocator = new idBlockAlloc<>(1024);
        //        public final idBlockAlloc<idInteraction> interactionAllocator = new idBlockAlloc<>(256);
        //        public idBlockAlloc<areaNumRef_s> areaNumRefAllocator = new idBlockAlloc<>(1024);
        // all light / entity interactions are referenced here for fast lookup without
        // having to crawl the doubly linked lists.  EnntityDefs are sequential for better
        // cache access, because the table is accessed by light in idRenderWorldLocal::CreateLightDefInteractions()
        // Growing this table is time consuming, so we add a pad value to the number
        // of entityDefs and lightDefs
        var interactionTable: Array<idInteraction?>?
        var interactionTableHeight // lightDefs
                : Int
        var interactionTableWidth // entityDefs
                : Int
        val lightDefs: idList<idRenderLightLocal?>? = idList()

        //
        //
        //
        val localModels: idList<idRenderModel?>? = idList()

        // virtual					~idRenderWorldLocal();
        var mapName // ie: maps/tim_dm2.proc, written to demoFile
                : idStr?
        var   /*ID_TIME_T*/mapTimeStamp // for fast reloads of the same level
                : LongArray?
        var numAreaNodes: Int
        var numInterAreaPortals: Int
        var numPortalAreas: Int

        //
        var portalAreas: Array<portalArea_s?>?

        /*
         ==============
         UpdateEntityDef

         Does not write to the demo file, which will only be updated for
         visible entities
         ==============
         */
        var c_callbackUpdate = 0
        override fun AddEntityDef(re: renderEntity_s?): Int {
            // try and reuse a free spot
            var entityHandle = entityDefs.FindNull()
            if (entityHandle == -1) {
                entityHandle = entityDefs.Append(null as idRenderEntityLocal?)
                if (interactionTable != null && entityDefs.Num() > interactionTableWidth) {
                    ResizeInteractionTable()
                }
            }
            UpdateEntityDef(entityHandle, re)
            return entityHandle
        }

        override fun UpdateEntityDef(entityHandle: Int, re: renderEntity_s?) {
            if (RenderSystem_init.r_skipUpdates.GetBool()) {
                return
            }
            tr_local.tr.pc.c_entityUpdates++
            if (TempDump.NOT(re.hModel) && TempDump.NOT(re.callback)) {
                Common.common.Error("idRenderWorld::UpdateEntityDef: NULL hModel")
            }

            // create new slots if needed
            if (entityHandle < 0 || entityHandle > RenderWorld_local.LUDICROUS_INDEX) {
                Common.common.Error("idRenderWorld::UpdateEntityDef: index = %d", entityHandle)
            }
            while (entityHandle >= entityDefs.Num()) {
                entityDefs.Append(null as idRenderEntityLocal?)
            }
            var def = entityDefs.oGet(entityHandle)
            if (def != null) {
                if (0 == re.forceUpdate) {

                    // check for exact match (OPTIMIZE: check through pointers more)
                    if (TempDump.NOT(*re.joints) && TempDump.NOT(re.callbackData) && TempDump.NOT(def.dynamicModel) && re == def.parms) {
                        return
                    }

                    // if the only thing that changed was shaderparms, we can just leave things as they are
                    // after updating parms
                    // if we have a callback function and the bounds, origin, axis and model match,
                    // then we can leave the references as they are
                    if (re.callback != null) {
                        val axisMatch = re.axis == def.parms.axis
                        val originMatch = re.origin == def.parms.origin
                        val boundsMatch = re.bounds == def.referenceBounds
                        val modelMatch = re.hModel === def.parms.hModel
                        if (boundsMatch && originMatch && axisMatch && modelMatch) {
                            // only clear the dynamic model and interaction surfaces if they exist
                            c_callbackUpdate++
                            tr_lightrun.R_ClearEntityDefDynamicModel(def)
                            def.parms = renderEntity_s(re)
                            return
                        }
                    }
                }

                // save any decals if the model is the same, allowing marks to move with entities
                if (def.parms.hModel === re.hModel) {
                    tr_lightrun.R_FreeEntityDefDerivedData(def, true, true)
                } else {
                    tr_lightrun.R_FreeEntityDefDerivedData(def, false, false)
                }
            } else {
                // creating a new one
                def = idRenderEntityLocal()
                entityDefs.oSet(entityHandle, def)
                def.world = this
                def.index = entityHandle
            }
            def.parms = renderEntity_s(re)
            //        TempDump.printCallStack("~~~~~~~~~~~~~~~~~" );
            tr_main.R_AxisToModelMatrix(def.parms.axis, def.parms.origin, def.modelMatrix)
            def.lastModifiedFrameNum = tr_local.tr.frameCount
            if (Session.Companion.session.writeDemo != null && def.archived) {
                WriteFreeEntity(entityHandle)
                def.archived = false
            }

            // optionally immediately issue any callbacks
            if (!RenderSystem_init.r_useEntityCallbacks.GetBool() && def.parms.callback != null) {
                tr_light.R_IssueEntityDefCallback(def)
            }

            // based on the model bounds, add references in each area
            // that may contain the updated surface
            tr_lightrun.R_CreateEntityRefs(def)
        }

        /*
         ===================
         FreeEntityDef

         Frees all references and lit surfaces from the model, and
         NULL's out it's entry in the world list
         ===================
         */
        override fun FreeEntityDef(entityHandle: Int) {
            val def: idRenderEntityLocal?
            if (entityHandle < 0 || entityHandle >= entityDefs.Num()) {
                Common.common.Printf("idRenderWorld::FreeEntityDef: handle %d > %d\n", entityHandle, entityDefs.Num())
                return
            }
            def = entityDefs.oGet(entityHandle)
            if (TempDump.NOT(def)) {
                Common.common.Printf("idRenderWorld::FreeEntityDef: handle %d is NULL\n", entityHandle)
                return
            }
            tr_lightrun.R_FreeEntityDefDerivedData(def, false, false)
            if (Session.Companion.session.writeDemo != null && def.archived) {
                WriteFreeEntity(entityHandle)
            }

            // if we are playing a demo, these will have been freed
            // in R_FreeEntityDefDerivedData(), otherwise the gui
            // object still exists in the game
            def.parms.gui[0] = null
            def.parms.gui[1] = null
            def.parms.gui[2] = null

//	delete def;
            entityDefs.oSet(entityHandle, null)
        }

        override fun GetRenderEntity(entityHandle: Int): renderEntity_s? {
            val def: idRenderEntityLocal?
            if (entityHandle < 0 || entityHandle >= entityDefs.Num()) {
                Common.common.Printf(
                    "idRenderWorld::GetRenderEntity: invalid handle %d [0, %d]\n",
                    entityHandle,
                    entityDefs.Num()
                )
                return null
            }
            def = entityDefs.oGet(entityHandle)
            if (TempDump.NOT(def)) {
                Common.common.Printf("idRenderWorld::GetRenderEntity: handle %d is NULL\n", entityHandle)
                return null
            }
            return def.parms
        }

        override fun AddLightDef(rlight: renderLight_s?): Int {
            // try and reuse a free spot
            var lightHandle = lightDefs.FindNull()
            if (lightHandle == -1) {
                lightHandle = lightDefs.Append(null as idRenderLightLocal?)
                if (interactionTable != null && lightDefs.Num() > interactionTableHeight) {
                    ResizeInteractionTable()
                }
            }
            UpdateLightDef(lightHandle, rlight)
            return lightHandle
        }

        /*
         =================
         UpdateLightDef

         The generation of all the derived interaction data will
         usually be deferred until it is visible in a scene

         Does not write to the demo file, which will only be done for visible lights
         =================
         */
        override fun UpdateLightDef(lightHandle: Int, rlight: renderLight_s?) {
            if (RenderSystem_init.r_skipUpdates.GetBool()) {
                return
            }
            tr_local.tr.pc.c_lightUpdates++

            // create new slots if needed
            if (lightHandle < 0 || lightHandle > RenderWorld_local.LUDICROUS_INDEX) {
                Common.common.Error("idRenderWorld::UpdateLightDef: index = %d", lightHandle)
            }
            while (lightHandle >= lightDefs.Num()) {
                lightDefs.Append(null as idRenderLightLocal?)
            }
            var justUpdate = false
            var light = lightDefs.oGet(lightHandle)
            if (light != null) {
                // if the shape of the light stays the same, we don't need to dump
                // any of our derived data, because shader parms are calculated every frame
                if (rlight.axis == light.parms.axis && rlight.end == light.parms.end && rlight.lightCenter == light.parms.lightCenter && rlight.lightRadius == light.parms.lightRadius && rlight.noShadows == light.parms.noShadows && rlight.origin == light.parms.origin && rlight.parallel == light.parms.parallel && rlight.pointLight == light.parms.pointLight && rlight.right == light.parms.right && rlight.start == light.parms.start && rlight.target == light.parms.target && rlight.up == light.parms.up && rlight.shader === light.lightShader && rlight.prelightModel === light.parms.prelightModel) {
                    justUpdate = true
                } else {
                    // if we are updating shadows, the prelight model is no longer valid
                    light.lightHasMoved = true
                    tr_lightrun.R_FreeLightDefDerivedData(light)
                }
            } else {
                // create a new one
                light = idRenderLightLocal()
                lightDefs.oSet(lightHandle, light)
                light.world = this
                light.index = lightHandle
            }
            light.parms = renderLight_s(rlight)
            light.lastModifiedFrameNum = tr_local.tr.frameCount
            if (Session.Companion.session.writeDemo != null && light.archived) {
                WriteFreeLight(lightHandle)
                light.archived = false
            }
            if (light.lightHasMoved) {
                light.parms.prelightModel = null
            }
            if (!justUpdate) {
                tr_lightrun.R_DeriveLightData(light)
                tr_lightrun.R_CreateLightRefs(light)
                tr_lightrun.R_CreateLightDefFogPortals(light)
            }
        }

        /*
         ====================
         FreeLightDef

         Frees all references and lit surfaces from the light, and
         NULL's out it's entry in the world list
         ====================
         */
        override fun FreeLightDef(lightHandle: Int) {
            val light: idRenderLightLocal?
            if (lightHandle < 0 || lightHandle >= lightDefs.Num()) {
                Common.common.Printf(
                    "idRenderWorld::FreeLightDef: invalid handle %d [0, %d]\n",
                    lightHandle,
                    lightDefs.Num()
                )
                return
            }
            light = lightDefs.oGet(lightHandle)
            if (TempDump.NOT(light)) {
                Common.common.Printf("idRenderWorld::FreeLightDef: handle %d is NULL\n", lightHandle)
                return
            }
            tr_lightrun.R_FreeLightDefDerivedData(light)
            if (Session.Companion.session.writeDemo != null && light.archived) {
                WriteFreeLight(lightHandle)
            }

            //delete light;
            lightDefs.oSet(lightHandle, null)
        }

        override fun GetRenderLight(lightHandle: Int): renderLight_s? {
            val def: idRenderLightLocal?
            if (lightHandle < 0 || lightHandle >= lightDefs.Num()) {
                Common.common.Printf("idRenderWorld::GetRenderLight: handle %d > %d\n", lightHandle, lightDefs.Num())
                return null
            }
            def = lightDefs.oGet(lightHandle)
            if (null == def) {
                Common.common.Printf("idRenderWorld::GetRenderLight: handle %d is NULL\n", lightHandle)
                return null
            }
            return def.parms
        }

        override fun CheckAreaForPortalSky(areaNum: Int): Boolean {
            var ref: areaReference_s?
            assert(areaNum >= 0 && areaNum < numPortalAreas)
            ref = portalAreas.get(areaNum).entityRefs.areaNext
            while (ref.entity != null) {
                assert(ref.area === portalAreas.get(areaNum))
                if (ref.entity != null && ref.entity.needsPortalSky) {
                    return true
                }
                ref = ref.areaNext
            }
            return false
        }

        /*
         ===================
         GenerateAllInteractions

         Force the generation of all light / surface interactions at the start of a level
         If this isn't called, they will all be dynamically generated

         This really isn't all that helpful anymore, because the calculation of shadows
         and light interactions is deferred from idRenderWorldLocal::CreateLightDefInteractions(), but we
         use it as an oportunity to size the interactionTable
         ===================
         */
        override fun GenerateAllInteractions() {
            if (!tr_local.glConfig.isInitialized) {
                return
            }
            val start = win_shared.Sys_Milliseconds()
            generateAllInteractionsCalled = false

            // watch how much memory we allocate
            tr_local.tr.staticAllocCount = 0

            // let idRenderWorldLocal::CreateLightDefInteractions() know that it shouldn't
            // try and do any view specific optimizations
            tr_local.tr.viewDef = null
            for (i in 0 until lightDefs.Num()) {
                val ldef = lightDefs.oGet(i)
                if (TempDump.NOT(ldef)) {
                    continue
                }
                CreateLightDefInteractions(ldef)
            }
            val end = win_shared.Sys_Milliseconds()
            val msec = end - start
            Common.common.Printf(
                "idRenderWorld::GenerateAllInteractions, msec = %d, staticAllocCount = %d.\n",
                msec,
                tr_local.tr.staticAllocCount
            )

            // build the interaction table
            if (RenderSystem_init.r_useInteractionTable.GetBool()) {
                interactionTableWidth = entityDefs.Num() + 100
                interactionTableHeight = lightDefs.Num() + 100
                val size = interactionTableWidth * interactionTableHeight //* sizeof(interactionTable);
                interactionTable = arrayOfNulls<idInteraction?>(size) // R_ClearedStaticAlloc(size);
                var count = 0
                for (i in 0 until lightDefs.Num()) {
                    val ldef = lightDefs.oGet(i)
                    if (TempDump.NOT(ldef)) {
                        continue
                    }
                    var inter: idInteraction?
                    inter = ldef.firstInteraction
                    while (inter != null) {
                        val edef = inter.entityDef
                        val index = ldef.index * interactionTableWidth + edef.index
                        interactionTable.get(index) = inter
                        count++
                        inter = inter.lightNext
                    }
                }
                Common.common.Printf("interactionTable size: %d bytes\n", size)
                Common.common.Printf("%d interaction take %d bytes\n", count, count /* sizeof(idInteraction)*/)
            }

            // entities flagged as noDynamicInteractions will no longer make any
            generateAllInteractionsCalled = true
        }

        override fun RegenerateWorld() {
            R_RegenerateWorld_f.Companion.getInstance().run(CmdArgs.idCmdArgs())
        }

        override fun ProjectDecalOntoWorld(
            winding: idFixedWinding?,
            projectionOrigin: idVec3?,
            parallel: Boolean,
            fadeDepth: Float,
            material: idMaterial?,
            startTime: Int
        ) {
            var i: Int
            val numAreas: Int
            val areas = IntArray(10)
            var ref: areaReference_s?
            var area: portalArea_s?
            var model: idRenderModel
            var def: idRenderEntityLocal?
            val info = decalProjectionInfo_s()
            val localInfo = decalProjectionInfo_s()
            if (!idRenderModelDecal.Companion.CreateProjectionInfo(
                    info,
                    winding,
                    projectionOrigin,
                    parallel,
                    fadeDepth,
                    material,
                    startTime
                )
            ) {
                return
            }

            // get the world areas touched by the projection volume
            numAreas = BoundsInAreas(info.projectionBounds, areas, 10)

            // check all areas for models
            i = 0
            while (i < numAreas) {
                area = portalAreas.get(areas[i])

                // check all models in this area
                ref = area.entityRefs.areaNext
                while (ref !== area.entityRefs) {
                    def = ref.entity

                    // completely ignore any dynamic or callback models
                    model = def.parms.hModel
                    if (model == null || model.IsDynamicModel() != dynamicModel_t.DM_STATIC || def.parms.callback != null) {
                        ref = ref.areaNext
                        continue
                    }
                    if (def.parms.customShader != null && !def.parms.customShader.AllowOverlays()) {
                        ref = ref.areaNext
                        continue
                    }
                    val bounds = idBounds()
                    bounds.FromTransformedBounds(model.Bounds(def.parms), def.parms.origin, def.parms.axis)

                    // if the model bounds do not overlap with the projection bounds
                    if (!info.projectionBounds.IntersectsBounds(bounds)) {
                        ref = ref.areaNext
                        continue
                    }

                    // transform the bounding planes, fade planes and texture axis into local space
                    idRenderModelDecal.Companion.GlobalProjectionInfoToLocal(
                        localInfo,
                        info,
                        def.parms.origin,
                        def.parms.axis
                    )
                    localInfo.force = def.parms.customShader != null
                    if (TempDump.NOT(def.decals)) {
                        def.decals = idRenderModelDecal.Companion.Alloc()
                    }
                    def.decals.CreateDecal(model, localInfo)
                    ref = ref.areaNext
                }
                i++
            }
        }

        override fun ProjectDecal(
            entityHandle: Int,
            winding: idFixedWinding?,
            projectionOrigin: idVec3?,
            parallel: Boolean,
            fadeDepth: Float,
            material: idMaterial?,
            startTime: Int
        ) {
            val info = decalProjectionInfo_s()
            val localInfo = decalProjectionInfo_s()
            if (entityHandle < 0 || entityHandle >= entityDefs.Num()) {
                Common.common.Error("idRenderWorld::ProjectOverlay: index = %d", entityHandle)
                return
            }
            val def = entityDefs.oGet(entityHandle)
            if (TempDump.NOT(def)) {
                return
            }
            val model = def.parms.hModel
            if (model == null || model.IsDynamicModel() != dynamicModel_t.DM_STATIC || def.parms.callback != null) {
                return
            }
            if (!idRenderModelDecal.Companion.CreateProjectionInfo(
                    info,
                    winding,
                    projectionOrigin,
                    parallel,
                    fadeDepth,
                    material,
                    startTime
                )
            ) {
                return
            }
            val bounds = idBounds()
            bounds.FromTransformedBounds(model.Bounds(def.parms), def.parms.origin, def.parms.axis)

            // if the model bounds do not overlap with the projection bounds
            if (!info.projectionBounds.IntersectsBounds(bounds)) {
                return
            }

            // transform the bounding planes, fade planes and texture axis into local space
            idRenderModelDecal.Companion.GlobalProjectionInfoToLocal(localInfo, info, def.parms.origin, def.parms.axis)
            localInfo.force = def.parms.customShader != null
            if (def.decals == null) {
                def.decals = idRenderModelDecal.Companion.Alloc()
            }
            def.decals.CreateDecal(model, localInfo)
        }

        override fun ProjectOverlay(entityHandle: Int, localTextureAxis: Array<idPlane?>?, material: idMaterial?) {
            if (entityHandle < 0 || entityHandle >= entityDefs.Num()) {
                Common.common.Error("idRenderWorld::ProjectOverlay: index = %d", entityHandle)
                return
            }
            val def = entityDefs.oGet(entityHandle)
            if (TempDump.NOT(def)) {
                return
            }
            val refEnt = def.parms
            var model = refEnt.hModel
            if (model.IsDynamicModel() != dynamicModel_t.DM_CACHED) {    // FIXME: probably should be MD5 only
                return
            }
            model = tr_light.R_EntityDefDynamicModel(def)
            if (def.overlay == null) {
                def.overlay = idRenderModelOverlay.Companion.Alloc()
            }
            def.overlay.CreateOverlay(model, localTextureAxis, material)
        }

        override fun RemoveDecals(entityHandle: Int) {
            if (entityHandle < 0 || entityHandle >= entityDefs.Num()) {
                Common.common.Error("idRenderWorld::ProjectOverlay: index = %d", entityHandle)
                return
            }
            val def = entityDefs.oGet(entityHandle)
            if (TempDump.NOT(def)) {
                return
            }
            tr_lightrun.R_FreeEntityDefDecals(def)
            tr_lightrun.R_FreeEntityDefOverlay(def)
        }

        /*
         ====================
         SetRenderView

         Sets the current view so any calls to the render world will use the correct parms.
         ====================
         */
        override fun SetRenderView(renderView: renderView_s?) {
            tr_local.tr.primaryRenderView = renderView_s(renderView)
        }

        /*
         ====================
         RenderScene

         Draw a 3D view into a part of the window, then return
         to 2D drawing.

         Rendering a scene may require multiple views to be rendered
         to handle mirrors,
         ====================
         */
        override fun RenderScene(renderView: renderView_s?) {
            if (!BuildDefines.ID_DEDICATED) {
                val copy: renderView_s
                if (!tr_local.glConfig.isInitialized) {
                    return
                }
                copy = renderView_s(renderView)

                // skip front end rendering work, which will result
                // in only gui drawing
                if (RenderSystem_init.r_skipFrontEnd.GetBool()) {
                    return
                }
                if (renderView.fov_x <= 0 || renderView.fov_y <= 0) {
                    Common.common.Error(
                        "idRenderWorld::RenderScene: bad FOVs: %f, %f",
                        renderView.fov_x,
                        renderView.fov_y
                    )
                }

                // close any gui drawing
                tr_local.tr.guiModel.EmitFullScreen()
                tr_local.tr.guiModel.Clear()
                val startTime = win_shared.Sys_Milliseconds()

                // setup view parms for the initial view
                //
                val parms = viewDef_s() // R_ClearedFrameAlloc(sizeof(parms));
                parms.renderView = renderView_s(renderView)
                if (tr_local.tr.takingScreenshot) {
                    parms.renderView.forceUpdate = true
                }

                // set up viewport, adjusted for resolution and OpenGL style 0 at the bottom
                tr_local.tr.RenderViewToViewport(parms.renderView, parms.viewport)

                // the scissor bounds may be shrunk in subviews even if
                // the viewport stays the same
                // this scissor range is local inside the viewport
                parms.scissor.x1 = 0
                parms.scissor.y1 = 0
                parms.scissor.x2 = parms.viewport.x2 - parms.viewport.x1
                parms.scissor.y2 = parms.viewport.y2 - parms.viewport.y1
                parms.isSubview = false
                parms.initialViewAreaOrigin.oSet(renderView.vieworg)
                parms.floatTime = parms.renderView.time * 0.001f
                parms.renderWorld = this

                // use this time for any subsequent 2D rendering, so damage blobs/etc
                // can use level time
                tr_local.tr.frameShaderTime = parms.floatTime

                // see if the view needs to reverse the culling sense in mirrors
                // or environment cube sides
                val cross = idVec3()
                cross.oSet(parms.renderView.viewaxis.oGet(1).Cross(parms.renderView.viewaxis.oGet(2)))
                parms.isMirror = cross.times(parms.renderView.viewaxis.oGet(0)) <= 0
                if (RenderSystem_init.r_lockSurfaces.GetBool()) {
                    RenderSystem.R_LockSurfaceScene(parms)
                    return
                }

                // save this world for use by some console commands
                tr_local.tr.primaryWorld = this
                tr_local.tr.primaryRenderView = renderView_s(renderView)
                tr_local.tr.primaryView = parms

                // rendering this view may cause other views to be rendered
                // for mirrors / portals / shadows / environment maps
                // this will also cause any necessary entities and lights to be
                // updated to the demo file
                tr_main.R_RenderView(parms)

                // now write delete commands for any modified-but-not-visible entities, and
                // add the renderView command to the demo
                if (Session.Companion.session.writeDemo != null) {
                    WriteRenderView(renderView)
                }

//                if (false) {
//                    for (int i = 0; i < entityDefs.Num(); i++) {
//                        idRenderEntityLocal def = entityDefs.oGet(i);
//                        if (!def) {
//                            continue;
//                        }
//                        if (def.parms.callback) {
//                            continue;
//                        }
//                        if (def.parms.hModel.IsDynamicModel() == DM_CONTINUOUS) {
//                        }
//                    }
//                }
                val endTime = win_shared.Sys_Milliseconds()
                tr_local.tr.pc.frontEndMsec += endTime - startTime

                // prepare for any 2D drawing after this
                tr_local.tr.guiModel.Clear()
            }
        }

        override fun NumAreas(): Int {
            return numPortalAreas
        }

        /*
         ===============
         PointInAreaNum

         Will return -1 if the point is not in an area, otherwise
         it will return 0 <= value < tr.world->numPortalAreas
         ===============
         */
        override fun PointInArea(point: idVec3?): Int {
            var node: areaNode_t?
            var nodeNum: Int
            var d: Float
            if (null == areaNodes) {
                return -1
            }
            node = areaNodes.get(0)
            while (true) {
                d = node.plane.Normal().times(point) + node.plane.oGet(3)
                nodeNum = if (d > 0) {
                    node.children.get(0)
                } else {
                    node.children.get(1)
                }
                if (nodeNum == 0) {
                    return -1 // in solid
                }
                if (nodeNum < 0) {
                    nodeNum = -1 - nodeNum
                    if (nodeNum >= numPortalAreas) {
                        Common.common.Error("idRenderWorld::PointInArea: area out of range")
                    }
                    return nodeNum
                }
                node = areaNodes.get(nodeNum)
            }

//            return -1;
        }

        /*
         ===================
         BoundsInAreas

         fills the *areas array with the number of the areas the bounds are in
         returns the total number of areas the bounds are in
         ===================
         */
        override fun BoundsInAreas(bounds: idBounds?, areas: IntArray?, maxAreas: Int): Int {
            val numAreas = IntArray(1)
            assert(areas != null)
            assert(
                bounds.oGet(0).oGet(0) <= bounds.oGet(1).oGet(0) && bounds.oGet(0).oGet(1) <= bounds.oGet(1)
                    .oGet(1) && bounds.oGet(0).oGet(2) <= bounds.oGet(1).oGet(2)
            )
            assert(
                bounds.oGet(1).oGet(0) - bounds.oGet(0).oGet(0) < 1e4f && bounds.oGet(1).oGet(1) - bounds.oGet(0)
                    .oGet(1) < 1e4f && bounds.oGet(1).oGet(2) - bounds.oGet(0).oGet(2) < 1e4f
            )
            if (null == areaNodes) {
                return 0
            }
            BoundsInAreas_r(0, bounds, areas, numAreas, maxAreas)
            return numAreas[0]
        }

        override fun NumPortalsInArea(areaNum: Int): Int {
            val area: portalArea_s?
            var count: Int
            var portal: portal_s?
            if (areaNum >= numPortalAreas || areaNum < 0) {
                Common.common.Error("idRenderWorld::NumPortalsInArea: bad areanum %d", areaNum)
            }
            area = portalAreas.get(areaNum)
            count = 0
            portal = area.portals
            while (portal != null) {
                count++
                portal = portal.next
            }
            return count
        }

        override fun GetPortal(areaNum: Int, portalNum: Int): exitPortal_t? {
            val area: portalArea_s?
            var count: Int
            var portal: portal_s?
            val ret = exitPortal_t()
            if (areaNum > numPortalAreas) {
                Common.common.Error("idRenderWorld::GetPortal: areaNum > numAreas")
            }
            area = portalAreas.get(areaNum)
            count = 0
            portal = area.portals
            while (portal != null) {
                if (count == portalNum) {
                    ret.areas[0] = areaNum
                    ret.areas[1] = portal.intoArea
                    ret.w = portal.w
                    ret.blockingBits = portal.doublePortal.blockingBits
                    ret.portalHandle = TempDump.indexOf(portal.doublePortal, doublePortals) + 1
                    return ret
                }
                count++
                portal = portal.next
            }
            Common.common.Error("idRenderWorld::GetPortal: portalNum > numPortals")

//            memset(ret, 0, sizeof(ret));
            return exitPortal_t()
        }

        /*
         ================
         GuiTrace

         checks a ray trace against any gui surfaces in an entity, returning the
         fraction location of the trace on the gui surface, or -1,-1 if no hit.
         this doesn't do any occlusion testing, simply ignoring non-gui surfaces.
         start / end are in global world coordinates.
         ================
         */
        override fun GuiTrace(entityHandle: Int, start: idVec3?, end: idVec3?): guiPoint_t? {
            var local: localTrace_t?
            val localStart = idVec3()
            val localEnd = idVec3()
            val bestPoint = idVec3()
            var j: Int
            val model: idRenderModel
            var tri: srfTriangles_s
            var shader: idMaterial?
            val pt = guiPoint_t()
            pt.y = -1f
            pt.x = pt.y
            pt.guiId = 0
            if (entityHandle < 0 || entityHandle >= entityDefs.Num()) {
                Common.common.Printf("idRenderWorld::GuiTrace: invalid handle %d\n", entityHandle)
                return pt
            }
            val def = entityDefs.oGet(entityHandle)
            if (TempDump.NOT(def)) {
                Common.common.Printf("idRenderWorld::GuiTrace: handle %d is NULL\n", entityHandle)
                return pt
            }
            model = def.parms.hModel
            if (def.parms.callback != null || TempDump.NOT(def.parms.hModel) || def.parms.hModel.IsDynamicModel() != dynamicModel_t.DM_STATIC) {
                return pt
            }

            // transform the points into local space
            tr_main.R_GlobalPointToLocal(def.modelMatrix, start, localStart)
            tr_main.R_GlobalPointToLocal(def.modelMatrix, end, localEnd)
            val best = 99999f
            val bestSurf: modelSurface_s? = null
            j = 0
            while (j < model.NumSurfaces()) {
                val surf = model.Surface(j)
                tri = surf.geometry
                if (null == tri) {
                    j++
                    continue
                }
                shader = RenderWorld.R_RemapShaderBySkin(surf.shader, def.parms.customSkin, def.parms.customShader)
                if (null == shader) {
                    j++
                    continue
                }
                // only trace against gui surfaces
                if (!shader.HasGui()) {
                    j++
                    continue
                }
                local = tr_trace.R_LocalTrace(localStart, localEnd, 0.0f, tri)
                if (local.fraction < 1.0) {
                    val origin = idVec3()
                    val axis: Array<idVec3?> = idVec3.Companion.generateArray(3)
                    val cursor = idVec3()
                    val axisLen = FloatArray(2)
                    tr_guisurf.R_SurfaceToTextureAxis(tri, origin, axis)
                    cursor.oSet(local.point.oMinus(origin))
                    axisLen[0] = axis[0].Length()
                    axisLen[1] = axis[1].Length()
                    pt.x = cursor.times(axis[0]) / (axisLen[0] * axisLen[0])
                    pt.y = cursor.times(axis[1]) / (axisLen[1] * axisLen[1])
                    pt.guiId = shader.GetEntityGui()
                    return pt
                }
                j++
            }
            return pt
        }

        override fun ModelTrace(
            trace: modelTrace_s?,
            entityHandle: Int,
            start: idVec3?,
            end: idVec3?,
            radius: Float
        ): Boolean {
            var i: Int
            var collisionSurface: Boolean
            var surf: modelSurface_s
            var localTrace: localTrace_t?
            val model: idRenderModel?
            val modelMatrix = FloatArray(16)
            val localStart = idVec3()
            val localEnd = idVec3()
            var shader: idMaterial?
            trace.fraction = 1.0f
            if (entityHandle < 0 || entityHandle >= entityDefs.Num()) {
//		common.Error( "idRenderWorld::ModelTrace: index = %i", entityHandle );
                return false
            }
            val def = entityDefs.oGet(entityHandle) ?: return false
            val refEnt = def.parms
            model = tr_light.R_EntityDefDynamicModel(def)
            if (null == model) {
                return false
            }

            // transform the points into local space
            tr_main.R_AxisToModelMatrix(refEnt.axis, refEnt.origin, modelMatrix)
            tr_main.R_GlobalPointToLocal(modelMatrix, start, localStart)
            tr_main.R_GlobalPointToLocal(modelMatrix, end, localEnd)

            // if we have explicit collision surfaces, only collide against them
            // (FIXME, should probably have a parm to control this)
            collisionSurface = false
            i = 0
            while (i < model.NumBaseSurfaces()) {
                surf = model.Surface(i)
                shader = RenderWorld.R_RemapShaderBySkin(surf.shader, def.parms.customSkin, def.parms.customShader)
                if (shader.GetSurfaceFlags() and Material.SURF_COLLISION != 0) {
                    collisionSurface = true
                    break
                }
                i++
            }

            // only use baseSurfaces, not any overlays
            i = 0
            while (i < model.NumBaseSurfaces()) {
                surf = model.Surface(i)
                shader = RenderWorld.R_RemapShaderBySkin(surf.shader, def.parms.customSkin, def.parms.customShader)
                if (null == surf.geometry || null == shader) {
                    i++
                    continue
                }
                if (collisionSurface) {
                    // only trace vs collision surfaces
                    if (0 == shader.GetSurfaceFlags() and Material.SURF_COLLISION) {
                        i++
                        continue
                    }
                } else {
                    // skip if not drawn or translucent
                    if (!shader.IsDrawn() || shader.Coverage() != materialCoverage_t.MC_OPAQUE && shader.Coverage() != materialCoverage_t.MC_PERFORATED) {
                        i++
                        continue
                    }
                }
                localTrace = tr_trace.R_LocalTrace(localStart, localEnd, radius, surf.geometry)
                if (localTrace.fraction < trace.fraction) {
                    trace.fraction = localTrace.fraction
                    trace.point.oSet(tr_main.R_LocalPointToGlobal(modelMatrix, localTrace.point))
                    trace.normal.oSet(localTrace.normal.times(refEnt.axis))
                    trace.material = shader
                    trace.entity = def.parms
                    trace.jointNumber = refEnt.hModel.NearestJoint(
                        i,
                        localTrace.indexes[0],
                        localTrace.indexes[1],
                        localTrace.indexes[2]
                    )
                }
                i++
            }
            return trace.fraction < 1.0f
        }

        override fun Trace(
            trace: modelTrace_s?,
            start: idVec3?,
            end: idVec3?,
            radius: Float,
            skipDynamic: Boolean,
            skipPlayer: Boolean /*_D3XP*/
        ): Boolean {
            var ref: areaReference_s?
            var def: idRenderEntityLocal?
            var area: portalArea_s?
            var model: idRenderModel?
            var tri: srfTriangles_s
            var localTrace: localTrace_t?
            val areas = IntArray(128)
            val numAreas: Int
            var i: Int
            var j: Int
            var numSurfaces: Int
            val traceBounds = idBounds()
            val bounds = idBounds()
            val modelMatrix = FloatArray(16)
            val localStart = idVec3()
            val localEnd = idVec3()
            var shader: idMaterial?
            trace.fraction = 1.0f
            trace.point.oSet(end)

            // bounds for the whole trace
            traceBounds.Clear()
            traceBounds.AddPoint(start)
            traceBounds.AddPoint(end)

            // get the world areas the trace is in
            numAreas = BoundsInAreas(traceBounds, areas, 128)
            numSurfaces = 0

            // check all areas for models
            i = 0
            while (i < numAreas) {
                area = portalAreas.get(areas[i])

                // check all models in this area
                ref = area.entityRefs.areaNext
                while (ref !== area.entityRefs) {
                    def = ref.entity
                    model = def.parms.hModel
                    if (null == model) {
                        ref = ref.areaNext
                        continue
                    }
                    if (model.IsDynamicModel() != dynamicModel_t.DM_STATIC) {
                        if (skipDynamic) {
                            ref = ref.areaNext
                            continue
                        }
                        if (true) {    /* _D3XP addition. could use a cleaner approach */
                            if (skipPlayer) {
                                val name = model.Name()
                                var exclude: String?
                                var k: Int
                                k = 0
                                while (playerModelExcludeList.size > k) {
                                    exclude = playerModelExcludeList.get(k)
                                    if (name == exclude) {
                                        break
                                    }
                                    k++
                                }
                                if (playerModelExcludeList.get(k) != null) {
                                    ref = ref.areaNext
                                    continue
                                }
                            }
                        }
                        model = tr_light.R_EntityDefDynamicModel(def)
                        if (null == model) {
                            ref = ref.areaNext
                            continue  // can happen with particle systems, which don't instantiate without a valid view
                        }
                    }
                    bounds.FromTransformedBounds(model.Bounds(def.parms), def.parms.origin, def.parms.axis)

                    // if the model bounds do not overlap with the trace bounds
                    if (!traceBounds.IntersectsBounds(bounds) || !bounds.LineIntersection(start, trace.point)) {
                        ref = ref.areaNext
                        continue
                    }

                    // check all model surfaces
                    j = 0
                    while (j < model.NumSurfaces()) {
                        val surf = model.Surface(j)
                        shader =
                            RenderWorld.R_RemapShaderBySkin(surf.shader, def.parms.customSkin, def.parms.customShader)

                        // if no geometry or no shader
                        if (null == surf.geometry || null == shader) {
                            j++
                            continue
                        }
                        if (true) { /* _D3XP addition. could use a cleaner approach */
                            if (skipPlayer) {
                                val name: String = shader.GetName()
                                var exclude: String?
                                var k: Int
                                k = 0
                                while (k < playerMaterialExcludeList.size) {
                                    exclude = playerMaterialExcludeList.get(k)
                                    if (name == exclude) {
                                        break
                                    }
                                    k++
                                }
                                if (playerMaterialExcludeList.get(k) != null) {
                                    j++
                                    continue
                                }
                            }
                        }
                        tri = surf.geometry
                        bounds.FromTransformedBounds(tri.bounds, def.parms.origin, def.parms.axis)

                        // if triangle bounds do not overlap with the trace bounds
                        if (!traceBounds.IntersectsBounds(bounds) || !bounds.LineIntersection(start, trace.point)) {
                            j++
                            continue
                        }
                        numSurfaces++

                        // transform the points into local space
                        tr_main.R_AxisToModelMatrix(def.parms.axis, def.parms.origin, modelMatrix)
                        tr_main.R_GlobalPointToLocal(modelMatrix, start, localStart)
                        tr_main.R_GlobalPointToLocal(modelMatrix, end, localEnd)
                        localTrace = tr_trace.R_LocalTrace(localStart, localEnd, radius, surf.geometry)
                        if (localTrace.fraction < trace.fraction) {
                            trace.fraction = localTrace.fraction
                            trace.point.oSet(tr_main.R_LocalPointToGlobal(modelMatrix, localTrace.point))
                            trace.normal.oSet(localTrace.normal.times(def.parms.axis))
                            trace.material = shader
                            trace.entity = def.parms
                            trace.jointNumber = model.NearestJoint(
                                j,
                                localTrace.indexes[0],
                                localTrace.indexes[1],
                                localTrace.indexes[2]
                            )
                            traceBounds.Clear()
                            traceBounds.AddPoint(start)
                            traceBounds.AddPoint(start.oPlus(end.oMinus(start).oMultiply(trace.fraction)))
                        }
                        j++
                    }
                    ref = ref.areaNext
                }
                i++
            }
            return trace.fraction < 1.0f
        }

        override fun FastWorldTrace(results: modelTrace_s?, start: idVec3?, end: idVec3?): Boolean {
//            memset(results, 0, sizeof(modelTrace_t));
            results.clear()
            results.fraction = 1.0f
            if (areaNodes != null) {
                RecurseProcBSP_r(results, -1, 0, 0.0f, 1.0f, start, end)
                return results.fraction < 1.0f
            }
            return false
        }

        override fun DebugClearLines(time: Int) {
            tr_rendertools.RB_ClearDebugLines(time)
            tr_rendertools.RB_ClearDebugText(time)
        }

        override fun DebugLine(color: idVec4?, start: idVec3?, end: idVec3?, lifetime: Int, depthTest: Boolean) {
            tr_rendertools.RB_AddDebugLine(color, start, end, lifetime, depthTest)
        }

        override fun DebugArrow(color: idVec4?, start: idVec3?, end: idVec3?, size: Int, lifetime: Int) {
            val forward = idVec3()
            val right = idVec3()
            val up = idVec3()
            val v1 = idVec3()
            val v2 = idVec3()
            var a: Float
            var s: Float
            var i: Int
            DebugLine(color, start, end, lifetime)
            if (RenderSystem_init.r_debugArrowStep.GetInteger() <= 10) {
                return
            }
            // calculate sine and cosine when step size changes
            if (arrowStep != RenderSystem_init.r_debugArrowStep.GetInteger()) {
                arrowStep = RenderSystem_init.r_debugArrowStep.GetInteger()
                i = 0
                a = 0f
                while (a < 360.0f) {
                    arrowCos.get(i) = idMath.Cos16(Math_h.DEG2RAD(a))
                    arrowSin.get(i) = idMath.Sin16(Math_h.DEG2RAD(a))
                    a += arrowStep.toFloat()
                    i++
                }
                arrowCos.get(i) = arrowCos.get(0)
                arrowSin.get(i) = arrowSin.get(0)
            }
            // draw a nice arrow
            forward.oSet(end.oMinus(start))
            forward.Normalize()
            forward.NormalVectors(right, up)
            i = 0
            a = 0f
            while (a < 360.0f) {
                s = 0.5f * size * arrowCos.get(i)
                v1.oSet(end.oMinus(forward.times(size.toFloat())))
                v1.oSet(v1.oPlus(right.times(s)))
                s = 0.5f * size * arrowSin.get(i)
                v1.oSet(v1.oPlus(up.times(s)))
                s = 0.5f * size * arrowCos.get(i + 1)
                v2.oSet(end.oMinus(forward.times(size.toFloat())))
                v2.oSet(v2.oPlus(right.times(s)))
                s = 0.5f * size * arrowSin.get(i + 1)
                v2.oSet(v2.oPlus(up.times(s)))
                DebugLine(color, v1, end, lifetime)
                DebugLine(color, v1, v2, lifetime)
                a += arrowStep.toFloat()
                i++
            }
        }

        override fun DebugWinding(
            color: idVec4?,
            w: idWinding?,
            origin: idVec3?,
            axis: idMat3?,
            lifetime: Int,
            depthTest: Boolean
        ) {
            var i: Int
            val point = idVec3()
            val lastPoint = idVec3()
            if (w.GetNumPoints() < 2) {
                return
            }
            lastPoint.oSet(origin.oPlus(w.oGet(w.GetNumPoints() - 1).ToVec3().times(axis)))
            i = 0
            while (i < w.GetNumPoints()) {
                point.oSet(origin.oPlus(w.oGet(i).ToVec3().times(axis)))
                DebugLine(color, lastPoint, point, lifetime, depthTest)
                lastPoint.oSet(point)
                i++
            }
        }

        override fun DebugCircle(
            color: idVec4?,
            origin: idVec3?,
            dir: idVec3?,
            radius: Float,
            numSteps: Int,
            lifetime: Int,
            depthTest: Boolean
        ) {
            var i: Int
            var a: Float
            val left = idVec3()
            val up = idVec3()
            val point = idVec3()
            val lastPoint = idVec3()
            dir.OrthogonalBasis(left, up)
            left.timesAssign(radius)
            up.timesAssign(radius)
            lastPoint.oSet(origin.oPlus(up))
            i = 1
            while (i <= numSteps) {
                a = idMath.TWO_PI * i / numSteps
                point.oSet(origin.oPlus(left.times(idMath.Sin16(a)).oPlus(up.times(idMath.Cos16(a)))))
                DebugLine(color, lastPoint, point, lifetime, depthTest)
                lastPoint.oSet(point)
                i++
            }
        }

        override fun DebugSphere(color: idVec4?, sphere: idSphere?, lifetime: Int, depthTest: Boolean) {
            var i: Int
            var j: Int
            var n: Int
            val num: Int
            var s: Float
            var c: Float
            val p = idVec3()
            val lastp = idVec3()
            num = 360 / 15
            val lastArray: Array<idVec3?> = idVec3.Companion.generateArray(num)
            lastArray[0].oSet(sphere.GetOrigin().oPlus(idVec3(0, 0, sphere.GetRadius())))
            n = 1
            while (n < num) {
                lastArray[n].oSet(lastArray[0])
                n++
            }
            i = 15
            while (i <= 360) {
                s = idMath.Sin16(Math_h.DEG2RAD(i.toFloat()))
                c = idMath.Cos16(Math_h.DEG2RAD(i.toFloat()))
                lastp.oSet(0, sphere.GetOrigin().oGet(0))
                lastp.oSet(1, sphere.GetOrigin().oGet(1) + sphere.GetRadius() * s)
                lastp.oSet(2, sphere.GetOrigin().oGet(2) + sphere.GetRadius() * c)
                n = 0
                j = 15
                while (j <= 360) {
                    p.oSet(
                        0,
                        sphere.GetOrigin().oGet(0) + idMath.Sin16(Math_h.DEG2RAD(j.toFloat())) * sphere.GetRadius() * s
                    )
                    p.oSet(
                        1,
                        sphere.GetOrigin().oGet(1) + idMath.Cos16(Math_h.DEG2RAD(j.toFloat())) * sphere.GetRadius() * s
                    )
                    p.oSet(2, lastp.oGet(2))
                    DebugLine(color, lastp, p, lifetime, depthTest)
                    DebugLine(color, lastp, lastArray[n], lifetime, depthTest)
                    lastArray[n].oSet(lastp)
                    lastp.oSet(p)
                    j += 15
                    n++
                }
                i += 15
            }
        }

        override fun DebugBounds(color: idVec4?, bounds: idBounds?, org: idVec3?, lifetime: Int) {
            var i: Int
            val v: Array<idVec3?> = idVec3.Companion.generateArray(8)
            if (bounds.IsCleared()) {
                return
            }
            i = 0
            while (i < 8) {
                v[i].oSet(0, org.oGet(0) + bounds.oGet(i xor (i shr 1) and 1).oGet(0))
                v[i].oSet(1, org.oGet(1) + bounds.oGet(i shr 1 and 1).oGet(1))
                v[i].oSet(2, org.oGet(2) + bounds.oGet(i shr 2 and 1).oGet(2))
                i++
            }
            i = 0
            while (i < 4) {
                DebugLine(color, v[i], v[i + 1 and 3], lifetime)
                DebugLine(color, v[4 + i], v[4 + (i + 1 and 3)], lifetime)
                DebugLine(color, v[i], v[4 + i], lifetime)
                i++
            }
        }

        override fun DebugBox(color: idVec4?, box: idBox?, lifetime: Int) {
            var i: Int
            val v: Array<idVec3?> = idVec3.Companion.generateArray(8)
            box.ToPoints(v)
            i = 0
            while (i < 4) {
                DebugLine(color, v[i], v[i + 1 and 3], lifetime)
                DebugLine(color, v[4 + i], v[4 + (i + 1 and 3)], lifetime)
                DebugLine(color, v[i], v[4 + i], lifetime)
                i++
            }
        }

        override fun DebugFrustum(color: idVec4?, frustum: idFrustum?, showFromOrigin: Boolean, lifetime: Int) {
            var i: Int
            val v: Array<idVec3?> = idVec3.Companion.generateArray(8)
            frustum.ToPoints(v)
            if (frustum.GetNearDistance() > 0.0f) {
                i = 0
                while (i < 4) {
                    DebugLine(color, v[i], v[i + 1 and 3], lifetime)
                    i++
                }
                if (showFromOrigin) {
                    i = 0
                    while (i < 4) {
                        DebugLine(color, frustum.GetOrigin(), v[i], lifetime)
                        i++
                    }
                }
            }
            i = 0
            while (i < 4) {
                DebugLine(color, v[4 + i], v[4 + (i + 1 and 3)], lifetime)
                DebugLine(color, v[i], v[4 + i], lifetime)
                i++
            }
        }

        /*
         ============
         idRenderWorldLocal::DebugCone

         dir is the cone axis
         radius1 is the radius at the apex
         radius2 is the radius at apex+dir
         ============
         */
        override fun DebugCone(
            color: idVec4?,
            apex: idVec3?,
            dir: idVec3?,
            radius1: Float,
            radius2: Float,
            lifetime: Int
        ) {
            var i: Int
            val axis = idMat3()
            val top = idVec3()
            val p1 = idVec3()
            val p2 = idVec3()
            val lastp1 = idVec3()
            val lastp2 = idVec3()
            val d = idVec3()
            axis.oSet(2, dir)
            axis.oGet(2).Normalize()
            axis.oGet(2).NormalVectors(axis.oGet(0), axis.oGet(1))
            axis.oSet(1, axis.oGet(1).oNegative())
            top.oSet(apex.oPlus(dir))
            lastp2.oSet(top.oPlus(axis.oGet(1).times(radius2)))
            if (radius1 == 0.0f) {
                i = 20
                while (i <= 360) {
                    d.oSet(
                        axis.oGet(0).times(idMath.Sin16(Math_h.DEG2RAD(i.toFloat())))
                            .oPlus(axis.oGet(1).times(idMath.Cos16(Math_h.DEG2RAD(i.toFloat()))))
                    )
                    p2.oSet(top.oPlus(d.times(radius2)))
                    DebugLine(color, lastp2, p2, lifetime)
                    DebugLine(color, p2, apex, lifetime)
                    lastp2.oSet(p2)
                    i += 20
                }
            } else {
                lastp1.oSet(apex.oPlus(axis.oGet(1).times(radius1)))
                i = 20
                while (i <= 360) {
                    d.oSet(
                        axis.oGet(0).times(idMath.Sin16(Math_h.DEG2RAD(i.toFloat())))
                            .oPlus(axis.oGet(1).times(idMath.Cos16(Math_h.DEG2RAD(i.toFloat()))))
                    )
                    p1.oSet(apex.oPlus(d.times(radius1)))
                    p2.oSet(top.oPlus(d.times(radius2)))
                    DebugLine(color, lastp1, p1, lifetime)
                    DebugLine(color, lastp2, p2, lifetime)
                    DebugLine(color, p1, p2, lifetime)
                    lastp1.oSet(p1)
                    lastp2.oSet(p2)
                    i += 20
                }
            }
        }

        @JvmOverloads
        fun DebugScreenRect(color: idVec4?, rect: idScreenRect?, viewDef: viewDef_s?, lifetime: Int = 0) {
            var i: Int
            val centerx: Float
            val centery: Float
            val dScale: Float
            val hScale: Float
            val vScale: Float
            val bounds = idBounds()
            val p: Array<idVec3?> = idVec3.Companion.generateArray(4)
            centerx = (viewDef.viewport.x2 - viewDef.viewport.x1) * 0.5f
            centery = (viewDef.viewport.y2 - viewDef.viewport.y1) * 0.5f
            dScale = RenderSystem_init.r_znear.GetFloat() + 1.0f
            hScale = dScale * idMath.Tan16(Math_h.DEG2RAD(viewDef.renderView.fov_x * 0.5f))
            vScale = dScale * idMath.Tan16(Math_h.DEG2RAD(viewDef.renderView.fov_y * 0.5f))
            bounds.oSet(
                0, 0,
                bounds.oSet(1, 0, dScale)
            )
            bounds.oSet(0, 1, -(rect.x1 - centerx) / centerx * hScale)
            bounds.oSet(1, 1, -(rect.x2 - centerx) / centerx * hScale)
            bounds.oSet(0, 2, (rect.y1 - centery) / centery * vScale)
            bounds.oSet(1, 2, (rect.y2 - centery) / centery * vScale)
            i = 0
            while (i < 4) {
                p[i].oSet(
                    idVec3(
                        bounds.oGet(0).oGet(0),
                        bounds.oGet(i xor (i shr 1) and 1).y,
                        bounds.oGet(i shr 1 and 1).z
                    )
                )
                p[i].oSet(viewDef.renderView.vieworg.oPlus(p[i].times(viewDef.renderView.viewaxis)))
                i++
            }
            i = 0
            while (i < 4) {
                DebugLine(color, p[i], p[i + 1 and 3], 0) //false);
                i++
            }
        }

        override fun DebugAxis(origin: idVec3?, axis: idMat3?) {
            val start = idVec3(origin)
            val end = idVec3(start.oPlus(axis.oGet(0).times(20.0f)))
            DebugArrow(Lib.Companion.colorWhite, start, end, 2)
            end.oSet(start.oPlus(axis.oGet(0).times(-20.0f)))
            DebugArrow(Lib.Companion.colorWhite, start, end, 2)
            end.oSet(start.oPlus(axis.oGet(1).times(20.0f)))
            DebugArrow(Lib.Companion.colorGreen, start, end, 2)
            end.oSet(start.oPlus(axis.oGet(1).times(-20.0f)))
            DebugArrow(Lib.Companion.colorGreen, start, end, 2)
            end.oSet(start.oPlus(axis.oGet(2).times(20.0f)))
            DebugArrow(Lib.Companion.colorBlue, start, end, 2)
            end.oSet(start.oPlus(axis.oGet(2).times(-20.0f)))
            DebugArrow(Lib.Companion.colorBlue, start, end, 2)
        }

        override fun DebugClearPolygons(time: Int) {
            tr_rendertools.RB_ClearDebugPolygons(time)
        }

        override fun DebugPolygon(color: idVec4?, winding: idWinding?, lifeTime: Int, depthTest: Boolean) {
            tr_rendertools.RB_AddDebugPolygon(color, winding, lifeTime, depthTest)
        }

        override fun DrawText(
            text: String?,
            origin: idVec3?,
            scale: Float,
            color: idVec4?,
            viewAxis: idMat3?,
            align: Int,
            lifetime: Int,
            depthTest: Boolean
        ) {
            throw UnsupportedOperationException("Not supported yet.")
        }

        //-----------------------
        // RenderWorld_load.cpp
        @Throws(idException::class)
        fun ParseModel(src: idLexer?): idRenderModel? {
            val model: idRenderModel?
            val token = idToken()
            var i: Int
            var j: Int
            var tri: srfTriangles_s?
            val surf = modelSurface_s()
            src.ExpectTokenString("{")

            // parse the name
            src.ExpectAnyToken(token)
            model = ModelManager.renderModelManager.AllocModel()
            model.InitEmpty(token.toString())
            val numSurfaces = src.ParseInt()
            if (numSurfaces < 0) {
                src.Error("R_ParseModel: bad numSurfaces")
            }
            i = 0
            while (i < numSurfaces) {
                src.ExpectTokenString("{")
                src.ExpectAnyToken(token)
                surf.shader = DeclManager.declManager.FindMaterial(token)
                surf.shader.AddReference()
                tri = tr_trisurf.R_AllocStaticTriSurf()
                surf.geometry = tri
                tri.numVerts = src.ParseInt()
                tri.numIndexes = src.ParseInt()
                tr_trisurf.R_AllocStaticTriSurfVerts(tri, tri.numVerts)
                j = 0
                while (j < tri.numVerts) {
                    val vec = FloatArray(8)
                    src.Parse1DMatrix(8, vec)
                    tri.verts[j].xyz.oSet(0, vec[0])
                    tri.verts[j].xyz.oSet(1, vec[1])
                    tri.verts[j].xyz.oSet(2, vec[2])
                    tri.verts[j].st.oSet(0, vec[3])
                    tri.verts[j].st.oSet(1, vec[4])
                    tri.verts[j].normal.oSet(0, vec[5])
                    tri.verts[j].normal.oSet(1, vec[6])
                    tri.verts[j].normal.oSet(2, vec[7])
                    j++
                }
                tr_trisurf.R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)
                j = 0
                while (j < tri.numIndexes) {
                    tri.indexes[j] = src.ParseInt()
                    j++
                }
                src.ExpectTokenString("}")

                // add the completed surface to the model
                model.AddSurface(surf)
                i++
            }
            src.ExpectTokenString("}")
            model.FinishSurfaces()
            return model
        }

        @Throws(idException::class)
        fun ParseShadowModel(src: idLexer?): idRenderModel? {
            val model: idRenderModel?
            val token = idToken()
            var j: Int
            val tri: srfTriangles_s?
            val surf = modelSurface_s()
            src.ExpectTokenString("{")

            // parse the name
            src.ExpectAnyToken(token)
            model = ModelManager.renderModelManager.AllocModel()
            model.InitEmpty(token.toString())
            surf.shader = tr_local.tr.defaultMaterial
            tri = tr_trisurf.R_AllocStaticTriSurf()
            surf.geometry = tri
            tri.numVerts = src.ParseInt()
            tri.numShadowIndexesNoCaps = src.ParseInt()
            tri.numShadowIndexesNoFrontCaps = src.ParseInt()
            tri.numIndexes = src.ParseInt()
            tri.shadowCapPlaneBits = src.ParseInt()
            tr_trisurf.R_AllocStaticTriSurfShadowVerts(tri, tri.numVerts)
            tri.bounds.Clear()
            tri.shadowVertexes = shadowCache_s.Companion.generateArray(tri.numVerts)
            j = 0
            while (j < tri.numVerts) {
                val vec = FloatArray(8)
                src.Parse1DMatrix(3, vec)
                tri.shadowVertexes[j].xyz.oSet(0, vec[0])
                tri.shadowVertexes[j].xyz.oSet(1, vec[1])
                tri.shadowVertexes[j].xyz.oSet(2, vec[2])
                tri.shadowVertexes[j].xyz.oSet(3, 1f) // no homogenous value
                tri.bounds.AddPoint(tri.shadowVertexes[j].xyz.ToVec3())
                val a = 0
                j++
            }
            tr_trisurf.R_AllocStaticTriSurfIndexes(tri, tri.numIndexes)
            j = 0
            while (j < tri.numIndexes) {
                tri.indexes[j] = src.ParseInt()
                j++
            }

            // add the completed surface to the model
            model.AddSurface(surf)
            src.ExpectTokenString("}")

            // we do NOT do a model.FinishSurfaceces, because we don't need sil edges, planes, tangents, etc.
//	model.FinishSurfaces();
            return model
        }

        fun SetupAreaRefs() {
            var i: Int
            connectedAreaNum = 0
            i = 0
            while (i < numPortalAreas) {
                portalAreas.get(i).areaNum = i
                portalAreas.get(i).lightRefs.areaPrev = portalAreas.get(i).lightRefs
                portalAreas.get(i).lightRefs.areaNext = portalAreas.get(i).lightRefs.areaPrev
                portalAreas.get(i).entityRefs.areaPrev = portalAreas.get(i).entityRefs
                portalAreas.get(i).entityRefs.areaNext = portalAreas.get(i).entityRefs.areaPrev
                i++
            }
        }

        @Throws(idException::class)
        fun ParseInterAreaPortals(src: idLexer?) {
            var i: Int
            var j: Int
            src.ExpectTokenString("{")
            numPortalAreas = src.ParseInt()
            if (numPortalAreas < 0) {
                src.Error("R_ParseInterAreaPortals: bad numPortalAreas")
                return
            }
            portalAreas = portalArea_s.generateArray(numPortalAreas)
            areaScreenRect = idScreenRect.Companion.generateArray(numPortalAreas)

            // set the doubly linked lists
            SetupAreaRefs()
            numInterAreaPortals = src.ParseInt()
            if (numInterAreaPortals < 0) {
                src.Error("R_ParseInterAreaPortals: bad numInterAreaPortals")
                return
            }
            doublePortals = TempDump.allocArray(doublePortal_s::class.java, numInterAreaPortals)
            i = 0
            while (i < numInterAreaPortals) {
                var numPoints: Int
                var a1: Int
                var a2: Int
                var w: idWinding
                var p: portal_s
                numPoints = src.ParseInt()
                a1 = src.ParseInt()
                a2 = src.ParseInt()
                w = idWinding(numPoints)
                w.SetNumPoints(numPoints)
                j = 0
                while (j < numPoints) {
                    src.Parse1DMatrix(3, w.oGet(j))
                    // no texture coordinates
                    w.oGet(j).oSet(3, 0f)
                    w.oGet(j).oSet(4, 0f)
                    j++
                }

                // add the portal to a1
                p = portal_s() // R_ClearedStaticAlloc(sizeof(p));
                p.intoArea = a2
                p.doublePortal = doublePortals.get(i)
                p.w = w
                p.w.GetPlane(p.plane)
                p.next = portalAreas.get(a1).portals
                portalAreas.get(a1).portals = p
                doublePortals.get(i).portals.get(0) = p

                // reverse it for a2
                p = portal_s() // R_ClearedStaticAlloc(sizeof(p));
                p.intoArea = a1
                p.doublePortal = doublePortals.get(i)
                p.w = w.Reverse()
                p.w.GetPlane(p.plane)
                p.next = portalAreas.get(a2).portals
                portalAreas.get(a2).portals = p
                doublePortals.get(i).portals.get(1) = p
                i++
            }
            src.ExpectTokenString("}")
        }

        @Throws(idException::class)
        fun ParseNodes(src: idLexer?) {
            src.ExpectTokenString("{")
            numAreaNodes = src.ParseInt()
            if (numAreaNodes < 0) {
                src.Error("R_ParseNodes: bad numAreaNodes")
            }
            areaNodes = TempDump.allocArray(areaNode_t::class.java, numAreaNodes)
            for (node in areaNodes) {
                src.Parse1DMatrix(4, node.plane)
                node.children.get(0) = src.ParseInt()
                node.children.get(1) = src.ParseInt()
            }
            src.ExpectTokenString("}")
        }

        fun CommonChildrenArea_r(node: areaNode_t?): Int {
            val nums = IntArray(2)
            for (i in 0..1) {
                if (node.children.get(i) <= 0) {
                    nums[i] = -1 - node.children.get(i)
                } else {
                    nums[i] = CommonChildrenArea_r(areaNodes.get(node.children.get(i)))
                }
            }

            // solid nodes will match any area
            if (nums[0] == RenderWorld_local.AREANUM_SOLID) {
                nums[0] = nums[1]
            }
            if (nums[1] == RenderWorld_local.AREANUM_SOLID) {
                nums[1] = nums[0]
            }
            val common: Int
            common = if (nums[0] == nums[1]) {
                nums[0]
            } else {
                RenderWorld_local.CHILDREN_HAVE_MULTIPLE_AREAS
            }
            node.commonChildrenArea = common
            return common
        }

        fun FreeWorld() {
            var i: Int

            // this will free all the lightDefs and entityDefs
            FreeDefs()

            // free all the portals and check light/model references
            i = 0
            while (i < numPortalAreas) {
                var area: portalArea_s?
                var portal: portal_s?
                var nextPortal: portal_s?
                area = portalAreas.get(i)
                portal = area.portals
                while (portal != null) {
                    //TODO:linkage?
                    nextPortal = portal.next
                    //			delete portal.w;
                    portal.w = null
                    portal = nextPortal
                }

                // there shouldn't be any remaining lightRefs or entityRefs
                if (area.lightRefs.areaNext !== area.lightRefs) {
                    Common.common.Error("FreeWorld: unexpected remaining lightRefs")
                }
                if (area.entityRefs.areaNext !== area.entityRefs) {
                    Common.common.Error("FreeWorld: unexpected remaining entityRefs")
                }
                i++
            }
            if (portalAreas != null) {
//                R_StaticFree(portalAreas);
                portalAreas = null
                numPortalAreas = 0
                //                R_StaticFree(areaScreenRect);
                areaScreenRect = null
            }
            if (doublePortals != null) {
//                R_StaticFree(doublePortals);
                doublePortals = null
                numInterAreaPortals = 0
            }
            if (areaNodes != null) {
//                R_StaticFree(areaNodes);
                areaNodes = null
            }

            // free all the inline idRenderModels
            i = 0
            while (i < localModels.Num()) {
                ModelManager.renderModelManager.RemoveModel(localModels.oGet(i))
                localModels.RemoveIndex(i)
                i++
            }
            localModels.Clear()

//            areaReferenceAllocator.Shutdown();
//            interactionAllocator.Shutdown();
//            areaNumRefAllocator.Shutdown();
            mapName.oSet("<FREED>")
        }

        /*
         =================
         idRenderWorldLocal::ClearWorld

         Sets up for a single area world
         =================
         */
        fun ClearWorld() {
            numPortalAreas = 1
            portalAreas = portalArea_s.generateArray(1)
            areaScreenRect = idScreenRect.Companion.generateArray(1)
            SetupAreaRefs()

            // even though we only have a single area, create a node
            // that has both children pointing at it so we don't need to
            //
            areaNodes = arrayOf(areaNode_t()) // R_ClearedStaticAlloc(sizeof(areaNodes[0]));
            areaNodes.get(0).plane.oSet(3, 1f)
            areaNodes.get(0).children.get(0) = -1
            areaNodes.get(0).children.get(1) = -1
        }

        /*
         =================
         idRenderWorldLocal::FreeDefs

         dump all the interactions
         =================
         */
        fun FreeDefs() {
            var i: Int
            generateAllInteractionsCalled = false
            if (interactionTable != null) {
//                R_StaticFree(interactionTable);
                interactionTable = null
            }

            // free all lightDefs
            i = 0
            while (i < lightDefs.Num()) {
                var light: idRenderLightLocal?
                light = lightDefs.oGet(i)
                if (light != null && light.world == this) {
                    FreeLightDef(i)
                    lightDefs.oSet(i, null)
                }
                i++
            }

            // free all entityDefs
            i = 0
            while (i < entityDefs.Num()) {
                var mod: idRenderEntityLocal?
                mod = entityDefs.oGet(i)
                if (mod != null && mod.world == this) {
                    FreeEntityDef(i)
                    entityDefs.oSet(i, null)
                }
                i++
            }
        }

        fun TouchWorldModels() {
            var i: Int
            i = 0
            while (i < localModels.Num()) {
                ModelManager.renderModelManager.CheckModel(localModels.oGet(i).Name())
                i++
            }
        }

        fun AddWorldModelEntities() {
            var i: Int

            // add the world model for each portal area
            // we can't just call AddEntityDef, because that would place the references
            // based on the bounding box, rather than explicitly into the correct area
            i = 0
            while (i < numPortalAreas) {
                var def: idRenderEntityLocal
                var index: Int
                def = idRenderEntityLocal()

                // try and reuse a free spot
                index = entityDefs.FindNull()
                if (index == -1) {
                    index = entityDefs.Append(def)
                } else {
                    entityDefs.oSet(index, def)
                }
                def.index = index
                def.world = this
                def.parms.hModel = ModelManager.renderModelManager.FindModel(Str.va("_area%d", i))
                if (def.parms.hModel.IsDefaultModel() || !def.parms.hModel.IsStaticWorldModel()) {
                    Common.common.Error("idRenderWorldLocal::InitFromMap: bad area model lookup")
                }
                val hModel = def.parms.hModel
                for (j in 0 until hModel.NumSurfaces()) {
                    val surf = hModel.Surface(j)
                    if ("textures/smf/portal_sky" == surf.shader.GetName()) {
                        def.needsPortalSky = true
                    }
                }
                def.referenceBounds.oSet(def.parms.hModel.Bounds())
                def.parms.axis.oSet(0, 0, 1f)
                def.parms.axis.oSet(1, 1, 1f)
                def.parms.axis.oSet(2, 2, 1f)
                tr_main.R_AxisToModelMatrix(def.parms.axis, def.parms.origin, def.modelMatrix)

                // in case an explicit shader is used on the world, we don't
                // want it to have a 0 alpha or color
                def.parms.shaderParms[3] = 1
                def.parms.shaderParms[2] = def.parms.shaderParms[3]
                def.parms.shaderParms[1] = def.parms.shaderParms[2]
                def.parms.shaderParms[0] = def.parms.shaderParms[1]
                AddEntityRefToArea(def, portalAreas.get(i))
                i++
            }
        }

        //--------------------------
        // RenderWorld_portals.cpp
        fun ClearPortalStates() {
            var i: Int
            var j: Int

            // all portals start off open
            i = 0
            while (i < numInterAreaPortals) {
                doublePortals.get(i).blockingBits = portalConnection_t.PS_BLOCK_NONE.ordinal
                i++
            }

            // flood fill all area connections
            i = 0
            while (i < numPortalAreas) {
                j = 0
                while (j < RenderWorld.NUM_PORTAL_ATTRIBUTES) {
                    connectedAreaNum++
                    FloodConnectedAreas(portalAreas.get(i), j)
                    j++
                }
                i++
            }
        }

        /*
         =================
         idRenderWorldLocal::InitFromMap

         A NULL or empty name will make a world without a map model, which
         is still useful for displaying a bare model
         =================
         */
        @Throws(idException::class)
        override fun InitFromMap(name: String?): Boolean {
            val src: idLexer
            val token = idToken()
            val filename: idStr
            var lastModel: idRenderModel?

            // if this is an empty world, initialize manually
            if (null == name || name.isEmpty()) {
                FreeWorld()
                mapName.Clear()
                ClearWorld()
                return true
            }

            // load it
            filename = idStr(name)
            filename.SetFileExtension(RenderWorld.PROC_FILE_EXT)

            // if we are reloading the same map, check the timestamp
            // and try to skip all the work
            val currentTimeStamp = LongArray(1)
            FileSystem_h.fileSystem.ReadFile(filename.toString(), null, currentTimeStamp)
            if (mapName == name) {
                if (currentTimeStamp[0] != FileSystem_h.FILE_NOT_FOUND_TIMESTAMP && currentTimeStamp[0] == mapTimeStamp.get(
                        0
                    )
                ) {
                    Common.common.Printf("idRenderWorldLocal::InitFromMap: retaining existing map\n")
                    FreeDefs()
                    TouchWorldModels()
                    AddWorldModelEntities()
                    ClearPortalStates()
                    return true
                }
                Common.common.Printf("idRenderWorldLocal::InitFromMap: timestamp has changed, reloading.\n")
            }
            FreeWorld()
            src = idLexer(filename.toString(), Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_NODOLLARPRECOMPILE)
            if (!src.IsLoaded()) {
                Common.common.Printf("idRenderWorldLocal::InitFromMap: %s not found\n", filename)
                ClearWorld()
                return false
            }
            mapName = idStr(name)
            mapTimeStamp.get(0) = currentTimeStamp[0]

            // if we are writing a demo, archive the load command
            if (Session.Companion.session.writeDemo != null) {
                WriteLoadMap()
            }
            if (!src.ReadToken(token) || token.Icmp(RenderWorld.PROC_FILE_ID) != 0) {
                Common.common.Printf(
                    "idRenderWorldLocal::InitFromMap: bad id '%s' instead of '%s'\n",
                    token,
                    RenderWorld.PROC_FILE_ID
                )
                //		delete src;
                return false
            }

            // parse the file
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token == "model") {
                    lastModel = ParseModel(src)

                    // add it to the model manager list
                    ModelManager.renderModelManager.AddModel(lastModel)

                    // save it in the list to free when clearing this map
                    localModels.Append(lastModel)
                    continue
                }
                if (token == "shadowModel") {
                    lastModel = ParseShadowModel(src)

                    // add it to the model manager list
                    ModelManager.renderModelManager.AddModel(lastModel)

                    // save it in the list to free when clearing this map
                    localModels.Append(lastModel)
                    continue
                }
                if (token == "interAreaPortals") {
                    ParseInterAreaPortals(src)
                    continue
                }
                if (token == "nodes") {
                    ParseNodes(src)
                    continue
                }
                src.Error("idRenderWorldLocal::InitFromMap: bad token \"%s\"", token)
            }

//	delete src;
            // if it was a trivial map without any areas, create a single area
            if (0 == numPortalAreas) {
                ClearWorld()
            }

            // find the points where we can early-our of reference pushing into the BSP tree
            CommonChildrenArea_r(areaNodes.get(0))
            AddWorldModelEntities()
            ClearPortalStates()

            // done!
            return true
        }

        fun ScreenRectFromWinding(w: idWinding?, space: viewEntity_s?): idScreenRect? {
            val r = idScreenRect()
            var i: Int
            val v = idVec3()
            val ndc = idVec3()
            var windowX: Float
            var windowY: Float
            r.Clear()
            i = 0
            while (i < w.GetNumPoints()) {
                v.oSet(tr_main.R_LocalPointToGlobal(space.modelMatrix, w.oGet(i).ToVec3()))
                tr_main.R_GlobalToNormalizedDeviceCoordinates(v, ndc)
                windowX =
                    0.5f * (1.0f + ndc.oGet(0)) * (tr_local.tr.viewDef.viewport.x2 - tr_local.tr.viewDef.viewport.x1)
                windowY =
                    0.5f * (1.0f + ndc.oGet(1)) * (tr_local.tr.viewDef.viewport.y2 - tr_local.tr.viewDef.viewport.y1)
                r.AddPoint(windowX, windowY)
                i++
            }
            r.Expand()
            return r
        }

        fun PortalIsFoggedOut(p: portal_s?): Boolean {
            val ldef: idRenderLightLocal?
            val w: idWinding?
            var i: Int
            val forward = idPlane()
            ldef = p.doublePortal.fogLight
            if (TempDump.NOT(ldef)) {
                return false
            }

            // find the current density of the fog
            val lightShader: idMaterial? = ldef.lightShader
            val size: Int = lightShader.GetNumRegisters()
            val regs = FloatArray(size)
            lightShader.EvaluateRegisters(regs, ldef.parms.shaderParms, tr_local.tr.viewDef, ldef.parms.referenceSound)
            val stage: shaderStage_t = lightShader.GetStage(0)
            val alpha = regs[stage.color.registers[3]]

            // if they left the default value on, set a fog distance of 500
            val a: Float
            a = if (alpha <= 1.0f) {
                -0.5f / tr_local.DEFAULT_FOG_DISTANCE
            } else {
                // otherwise, distance = alpha color
                -0.5f / alpha
            }
            forward.oSet(0, a * tr_local.tr.viewDef.worldSpace.modelViewMatrix[2])
            forward.oSet(1, a * tr_local.tr.viewDef.worldSpace.modelViewMatrix[6])
            forward.oSet(2, a * tr_local.tr.viewDef.worldSpace.modelViewMatrix[10])
            forward.oSet(3, a * tr_local.tr.viewDef.worldSpace.modelViewMatrix[14])
            w = p.w
            i = 0
            while (i < w.GetNumPoints()) {
                var d: Float
                d = forward.Distance(w.oGet(i).ToVec3())
                if (d < 0.5f) {
                    return false // a point not clipped off
                }
                i++
            }
            return true
        }

        fun FloodViewThroughArea_r(origin: idVec3?, areaNum: Int, ps: portalStack_s?) {
            var p: portal_s?
            var d: Float
            val area: portalArea_s?
            var check: portalStack_s?
            var newStack = portalStack_s()
            var i: Int
            var j: Int
            val v1 = idVec3()
            val v2 = idVec3()
            var addPlanes: Int
            var w: idFixedWinding // we won't overflow because MAX_PORTAL_PLANES = 20
            area = portalAreas.get(areaNum)

            // cull models and lights to the current collection of planes
            AddAreaRefs(areaNum, ps)
            if (areaScreenRect.get(areaNum).IsEmpty()) {
                areaScreenRect.get(areaNum) = idScreenRect(ps.rect)
            } else {
                areaScreenRect.get(areaNum).Union(ps.rect)
            }

            // go through all the portals
            p = area.portals
            while (p != null) {

                // an enclosing door may have sealed the portal off
                if (p.doublePortal.blockingBits and portalConnection_t.PS_BLOCK_VIEW.ordinal != 0) {
                    p = p.next
                    continue
                }

                // make sure this portal is facing away from the view
                d = p.plane.Distance(origin)
                if (d < -0.1f) {
                    p = p.next
                    continue
                }

                // make sure the portal isn't in our stack trace,
                // which would cause an infinite loop
                check = ps
                while (check != null) {
                    if (p == check.p) {
                        break // don't recursively enter a stack
                    }
                    check = check.next
                }
                if (check != null) {
                    p = p.next
                    continue  // already in stack
                }

                // if we are very close to the portal surface, don't bother clipping
                // it, which tends to give epsilon problems that make the area vanish
                if (d < 1.0f) {

                    // go through this portal
                    newStack = portalStack_s(ps)
                    newStack.p = p
                    newStack.next = ps
                    FloodViewThroughArea_r(origin, p.intoArea, newStack)
                    p = p.next
                    continue
                }

                // clip the portal winding to all of the planes
                w = idFixedWinding(p.w)
                j = 0
                while (j < ps.numPortalPlanes) {
                    if (!w.ClipInPlace(ps.portalPlanes[j].oNegative(), 0f)) {
                        break
                    }
                    j++
                }
                if (0 == w.GetNumPoints()) {
                    p = p.next
                    continue  // portal not visible
                }

                // see if it is fogged out
                if (PortalIsFoggedOut(p)) {
                    p = p.next
                    continue
                }

                // go through this portal
                newStack.p = p
                newStack.next = ps

                // find the screen pixel bounding box of the remaining portal
                // so we can scissor things outside it
                newStack.rect = ScreenRectFromWinding(w, tr_local.tr.identitySpace)

                // slop might have spread it a pixel outside, so trim it back
                newStack.rect.Intersect(ps.rect)

                // generate a set of clipping planes that will further restrict
                // the visible view beyond just the scissor rect
                addPlanes = w.GetNumPoints()
                if (addPlanes > RenderWorld_portals.MAX_PORTAL_PLANES) {
                    addPlanes = RenderWorld_portals.MAX_PORTAL_PLANES
                }
                newStack.numPortalPlanes = 0
                i = 0
                while (i < addPlanes) {
                    j = i + 1
                    if (j == w.GetNumPoints()) {
                        j = 0
                    }
                    v1.oSet(origin.oMinus(w.oGet(i).ToVec3()))
                    v2.oSet(origin.oMinus(w.oGet(j).ToVec3()))
                    newStack.portalPlanes[newStack.numPortalPlanes].Normal().Cross(v2, v1)

                    // if it is degenerate, skip the plane
                    if (newStack.portalPlanes[newStack.numPortalPlanes].Normalize() < 0.01f) {
                        i++
                        continue
                    }
                    newStack.portalPlanes[newStack.numPortalPlanes].FitThroughPoint(origin)
                    newStack.numPortalPlanes++
                    i++
                }

                // the last stack plane is the portal plane
                newStack.portalPlanes[newStack.numPortalPlanes] = idPlane(p.plane)
                newStack.numPortalPlanes++
                FloodViewThroughArea_r(origin, p.intoArea, newStack)
                p = p.next
            }
        }

        /*
         =======================
         FlowViewThroughPortals

         Finds viewLights and viewEntities by flowing from an origin through the visible portals.
         origin point can see into.  The planes array defines a volume (positive
         sides facing in) that should contain the origin, such as a view frustum or a point light box.
         Zero planes assumes an unbounded volume.
         =======================
         */
        fun FlowViewThroughPortals(origin: idVec3?, numPlanes: Int, planes: Array<idPlane?>?) {
            val ps = portalStack_s()
            var i: Int
            ps.next = null
            ps.p = null
            i = 0
            while (i < numPlanes) {
                ps.portalPlanes[i] = idPlane(planes.get(i))
                i++
            }
            ps.numPortalPlanes = numPlanes
            ps.rect = idScreenRect(tr_local.tr.viewDef.scissor)
            if (tr_local.tr.viewDef.areaNum < 0) {
                i = 0
                while (i < numPortalAreas) {
                    areaScreenRect.get(i) = idScreenRect(tr_local.tr.viewDef.scissor) //TODO:copy constructor?
                    i++
                }

                // if outside the world, mark everything
                i = 0
                while (i < numPortalAreas) {
                    AddAreaRefs(i, ps)
                    i++
                }
            } else {
                i = 0
                while (i < numPortalAreas) {
                    areaScreenRect.get(i).Clear()
                    i++
                }

                // flood out through portals, setting area viewCount
                FloodViewThroughArea_r(origin, tr_local.tr.viewDef.areaNum, ps)
            }
        }

        fun FloodLightThroughArea_r(light: idRenderLightLocal?, areaNum: Int, ps: portalStack_s?) {
            var p: portal_s?
            var d: Float
            val area: portalArea_s?
            var check: portalStack_s?
            var firstPortalStack: portalStack_s? = portalStack_s()
            var newStack = portalStack_s()
            var i: Int
            var j: Int
            val v1 = idVec3()
            val v2 = idVec3()
            var addPlanes: Int
            var w: idFixedWinding // we won't overflow because MAX_PORTAL_PLANES = 20
            area = portalAreas.get(areaNum)

            // add an areaRef
            AddLightRefToArea(light, area)

            // go through all the portals
            p = area.portals
            while (p != null) {

                // make sure this portal is facing away from the view
                d = p.plane.Distance(light.globalLightOrigin)
                if (d < -0.1f) {
                    p = p.next
                    continue
                }

                // make sure the portal isn't in our stack trace,
                // which would cause an infinite loop
                check = ps
                while (check != null) {
                    firstPortalStack = check
                    if (check.p === p) {
                        break // don't recursively enter a stack
                    }
                    check = check.next
                }
                if (check != null) {
                    p = p.next
                    continue  // already in stack
                }

                // if we are very close to the portal surface, don't bother clipping
                // it, which tends to give epsilon problems that make the area vanish
                if (d < 1.0f) {
                    // go through this portal
                    newStack = portalStack_s(ps)
                    newStack.p = p
                    newStack.next = ps
                    FloodLightThroughArea_r(light, p.intoArea, newStack)
                    p = p.next
                    continue
                }

                // clip the portal winding to all of the planes
                w = idFixedWinding(p.w)
                j = 0
                while (j < ps.numPortalPlanes) {
                    if (!w.ClipInPlace(ps.portalPlanes[j].oNegative(), 0f)) {
                        break
                    }
                    j++
                }
                if (0 == w.GetNumPoints()) {
                    p = p.next
                    continue  // portal not visible
                }
                // also always clip to the original light planes, because they aren't
                // necessarily extending to infinitiy like a view frustum
                j = 0
                while (j < firstPortalStack.numPortalPlanes) {
                    if (!w.ClipInPlace(firstPortalStack.portalPlanes[j].oNegative(), 0f)) {
                        break
                    }
                    j++
                }
                if (0 == w.GetNumPoints()) {
                    p = p.next
                    continue  // portal not visible
                }

                // go through this portal
                newStack.p = p
                newStack.next = ps

                // generate a set of clipping planes that will further restrict
                // the visible view beyond just the scissor rect
                addPlanes = w.GetNumPoints()
                if (addPlanes > RenderWorld_portals.MAX_PORTAL_PLANES) {
                    addPlanes = RenderWorld_portals.MAX_PORTAL_PLANES
                }
                newStack.numPortalPlanes = 0
                i = 0
                while (i < addPlanes) {
                    j = i + 1
                    if (j == w.GetNumPoints()) {
                        j = 0
                    }
                    v1.oSet(light.globalLightOrigin.oMinus(w.oGet(i).ToVec3()))
                    v2.oSet(light.globalLightOrigin.oMinus(w.oGet(j).ToVec3()))
                    newStack.portalPlanes[newStack.numPortalPlanes].Normal().Cross(v2, v1)

                    // if it is degenerate, skip the plane
                    if (newStack.portalPlanes[newStack.numPortalPlanes].Normalize() < 0.01f) {
                        i++
                        continue
                    }
                    newStack.portalPlanes[newStack.numPortalPlanes].FitThroughPoint(light.globalLightOrigin)
                    newStack.numPortalPlanes++
                    i++
                }
                FloodLightThroughArea_r(light, p.intoArea, newStack)
                p = p.next
            }
        }

        /*
         =======================
         FlowLightThroughPortals

         Adds an arearef in each area that the light center flows into.
         This can only be used for shadow casting lights that have a generated
         prelight, because shadows are cast from back side which may not be in visible areas.
         =======================
         */
        fun FlowLightThroughPortals(light: idRenderLightLocal?) {
            val ps: portalStack_s
            var i: Int
            val origin = idVec3(light.globalLightOrigin)

            // if the light origin areaNum is not in a valid area,
            // the light won't have any area refs
            if (light.areaNum == -1) {
                return
            }

//            memset(ps, 0, sizeof(ps));
            ps = portalStack_s()
            ps.numPortalPlanes = 6
            i = 0
            while (i < 6) {
                ps.portalPlanes[i] = idPlane(light.frustum[i])
                i++
            }
            FloodLightThroughArea_r(light, light.areaNum, ps)
        }

        fun FloodFrustumAreas_r(
            frustum: idFrustum?,
            areaNum: Int,
            bounds: idBounds?,
            areas: areaNumRef_s?
        ): areaNumRef_s? {
            var areas = areas
            var p: portal_s?
            val portalArea: portalArea_s?
            val newBounds = idBounds()
            var a: areaNumRef_s?
            portalArea = portalAreas.get(areaNum)

            // go through all the portals
            p = portalArea.portals
            while (p != null) {


                // check if we already visited the area the portal leads to
                a = areas
                while (a != null) {
                    if (a.areaNum == p.intoArea) {
                        break
                    }
                    a = a.next
                }
                if (a != null) {
                    p = p.next
                    continue
                }

                // the frustum origin must be at the front of the portal plane
                if (p.plane.Side(frustum.GetOrigin(), 0.1f) == Plane.SIDE_BACK) {
                    p = p.next
                    continue
                }

                // the frustum must cross the portal plane
                if (frustum.PlaneSide(p.plane, 0.0f) != Plane.PLANESIDE_CROSS) {
                    p = p.next
                    continue
                }

                // get the bounds for the portal winding projected in the frustum
                frustum.ProjectionBounds(p.w, newBounds)
                newBounds.IntersectSelf(bounds)
                if (newBounds.oGet(0).oGet(0) > newBounds.oGet(1).oGet(0) || newBounds.oGet(0).oGet(1) > newBounds.oGet(
                        1
                    ).oGet(1) || newBounds.oGet(0).oGet(2) > newBounds.oGet(1).oGet(2)
                ) {
                    p = p.next
                    continue
                }
                newBounds.oSet(1, 0, frustum.GetFarDistance())
                a = areaNumRef_s() //areaNumRefAllocator.Alloc();
                a.areaNum = p.intoArea
                a.next = areas
                areas = a
                areas = FloodFrustumAreas_r(frustum, p.intoArea, newBounds, areas)
                p = p.next
            }
            return areas
        }

        /*
         ===================
         idRenderWorldLocal::FloodFrustumAreas

         Retrieves all the portal areas the frustum floods into where the frustum starts in the given areas.
         All portals are assumed to be open.
         ===================
         */
        fun FloodFrustumAreas(frustum: idFrustum?, areas: areaNumRef_s?): areaNumRef_s? {
            var areas = areas
            val bounds = idBounds()
            var a: areaNumRef_s?

            // bounds that cover the whole frustum
            bounds.oGet(0).Set(frustum.GetNearDistance(), -1.0f, -1.0f)
            bounds.oGet(1).Set(frustum.GetFarDistance(), 1.0f, 1.0f)
            a = areas
            while (a != null) {
                areas = FloodFrustumAreas_r(frustum, a.areaNum, bounds, areas)
                a = a.next
            }
            return areas
        }

        /*
         ================
         CullEntityByPortals

         Return true if the entity reference bounds do not intersect the current portal chain.
         ================
         */
        fun CullEntityByPortals(entity: idRenderEntityLocal?, ps: portalStack_s?): Boolean {
            return if (!RenderSystem_init.r_useEntityCulling.GetBool()) {
                false
            } else tr_main.R_CullLocalBox(
                entity.referenceBounds,
                entity.modelMatrix,
                ps.numPortalPlanes,
                ps.portalPlanes
            )

            // try to cull the entire thing using the reference bounds.
            // we do not yet do callbacks or dynamic model creation,
            // because we want to do all touching of the model after
            // we have determined all the lights that may effect it,
            // which optimizes cache usage
        }

        /*
         ===================
         AddAreaEntityRefs

         Any models that are visible through the current portalStack will
         have their scissor
         ===================
         */
        fun AddAreaEntityRefs(areaNum: Int, ps: portalStack_s?) {
            var ref: areaReference_s?
            var entity: idRenderEntityLocal?
            val area: portalArea_s?
            var vEnt: viewEntity_s?
            //            idBounds b;
            area = portalAreas.get(areaNum)
            ref = area.entityRefs.areaNext
            while (ref !== area.entityRefs) {
                entity = ref.entity

                // debug tool to allow viewing of only one entity at a time
                if (RenderSystem_init.r_singleEntity.GetInteger() >= 0 && RenderSystem_init.r_singleEntity.GetInteger() != entity.index) {
                    ref = ref.areaNext
                    continue
                }

                // remove decals that are completely faded away
                tr_lightrun.R_FreeEntityDefFadedDecals(entity, tr_local.tr.viewDef.renderView.time)

                // check for completely suppressing the model
                if (!RenderSystem_init.r_skipSuppress.GetBool()) {
                    if (entity.parms.suppressSurfaceInViewID != 0 && entity.parms.suppressSurfaceInViewID == tr_local.tr.viewDef.renderView.viewID) {
                        ref = ref.areaNext
                        continue
                    }
                    if (entity.parms.allowSurfaceInViewID != 0 && entity.parms.allowSurfaceInViewID != tr_local.tr.viewDef.renderView.viewID) {
                        ref = ref.areaNext
                        continue
                    }
                }

                // cull reference bounds
                if (CullEntityByPortals(entity, ps)) {
                    // we are culled out through this portal chain, but it might
                    // still be visible through others
                    ref = ref.areaNext
                    continue
                }
                vEnt = tr_light.R_SetEntityDefViewEntity(entity)

                // possibly expand the scissor rect
                vEnt.scissorRect.Union(ps.rect)
                ref = ref.areaNext
            }
        }

        /*
         ================
         CullLightByPortals

         Return true if the light frustum does not intersect the current portal chain.
         The last stack plane is not used because lights are not near clipped.
         ================
         */
        fun CullLightByPortals(light: idRenderLightLocal?, ps: portalStack_s?): Boolean {
            var i: Int
            var j: Int
            val tri: srfTriangles_s?
            var d: Float
            val w = idFixedWinding() // we won't overflow because MAX_PORTAL_PLANES = 20
            if (RenderSystem_init.r_useLightCulling.GetInteger() == 0) {
                return false
            }
            if (RenderSystem_init.r_useLightCulling.GetInteger() >= 2) {
                // exact clip of light faces against all planes
                i = 0
                while (i < 6) {

                    // the light frustum planes face out from the light,
                    // so the planes that have the view origin on the negative
                    // side will be the "back" faces of the light, which must have
                    // some fragment inside the portalStack to be visible
                    if (light.frustum[i].Distance(tr_local.tr.viewDef.renderView.vieworg) >= 0) {
                        i++
                        continue
                    }

                    // get the exact winding for this side
                    val ow = light.frustumWindings[i]

                    // projected lights may have one of the frustums degenerated
                    if (null == ow) {
                        i++
                        continue
                    }
                    w.oSet(ow)

                    // now check the winding against each of the portalStack planes
                    j = 0
                    while (j < ps.numPortalPlanes - 1) {
                        if (!w.ClipInPlace(ps.portalPlanes[j].oNegative())) {
                            break
                        }
                        j++
                    }
                    if (w.GetNumPoints() != 0) {
                        // part of the winding is visible through the portalStack,
                        // so the light is not culled
                        return false
                    }
                    i++
                }
                // none of the light surfaces were visible
                return true
            } else {

                // simple point check against each plane
                tri = light.frustumTris

                // check against frustum planes
                i = 0
                while (i < ps.numPortalPlanes - 1) {
                    j = 0
                    while (j < tri.numVerts) {
                        d = ps.portalPlanes[i].Distance(tri.verts[j].xyz)
                        if (d < 0.0f) {
                            break // point is inside this plane
                        }
                        j++
                    }
                    if (j == tri.numVerts) {
                        // all points were outside one of the planes
                        tr_local.tr.pc.c_box_cull_out++
                        return true
                    }
                    i++
                }
            }
            return false
        }

        fun AddAreaLightRefs(areaNum: Int, ps: portalStack_s?) {
            var lref: areaReference_s?
            val area: portalArea_s?
            var light: idRenderLightLocal?
            var vLight: viewLight_s?
            DEBUG_AddAreaLightRefs++
            area = portalAreas.get(areaNum)
            lref = area.lightRefs.areaNext
            while (lref !== area.lightRefs) {
                light = lref.light

                // debug tool to allow viewing of only one light at a time
                if (RenderSystem_init.r_singleLight.GetInteger() >= 0 && RenderSystem_init.r_singleLight.GetInteger() != light.index) {
                    lref = lref.areaNext
                    continue
                }

                // check for being closed off behind a door
                // a light that doesn't cast shadows will still light even if it is behind a door
                if (RenderSystem_init.r_useLightCulling.GetInteger() >= 3 && !light.parms.noShadows && light.lightShader.LightCastsShadows()
                    && light.areaNum != -1 && !tr_local.tr.viewDef.connectedAreas[light.areaNum]
                ) {
                    lref = lref.areaNext
                    continue
                }

                // cull frustum
                if (CullLightByPortals(light, ps)) {
                    // we are culled out through this portal chain, but it might
                    // still be visible through others
                    lref = lref.areaNext
                    continue
                }
                vLight = tr_light.R_SetLightDefViewLight(light)

                // expand the scissor rect
                vLight.scissorRect.Union(ps.rect)
                lref = lref.areaNext
            }
        }

        /*
         ===================
         AddAreaRefs

         This may be entered multiple times with different planes
         if more than one portal sees into the area
         ===================
         */
        fun AddAreaRefs(areaNum: Int, ps: portalStack_s?) {
            // mark the viewCount, so r_showPortals can display the
            // considered portals
            portalAreas.get(areaNum).viewCount = tr_local.tr.viewCount

            // add the models and lights, using more precise culling to the planes
            AddAreaEntityRefs(areaNum, ps)
            AddAreaLightRefs(areaNum, ps)
        }

        fun BuildConnectedAreas_r(areaNum: Int) {
            val area: portalArea_s?
            var portal: portal_s?
            if (tr_local.tr.viewDef.connectedAreas[areaNum]) {
                return
            }
            tr_local.tr.viewDef.connectedAreas[areaNum] = true

            // flood through all non-blocked portals
            area = portalAreas.get(areaNum)
            portal = area.portals
            while (portal != null) {
                if (0 == portal.doublePortal.blockingBits and portalConnection_t.PS_BLOCK_VIEW.ordinal) {
                    BuildConnectedAreas_r(portal.intoArea)
                }
                portal = portal.next
            }
        }

        /*
         ===================
         BuildConnectedAreas

         This is only valid for a given view, not all views in a frame
         ===================
         */
        fun BuildConnectedAreas() {
            var i: Int
            tr_local.tr.viewDef.connectedAreas = BooleanArray(numPortalAreas)

            // if we are outside the world, we can see all areas
            if (tr_local.tr.viewDef.areaNum == -1) {
                Arrays.fill(tr_local.tr.viewDef.connectedAreas, true)
                return
            }

            // start with none visible, and flood fill from the current area
//            memset(tr.viewDef.connectedAreas, 0, numPortalAreas);
            tr_local.tr.viewDef.connectedAreas = BooleanArray(numPortalAreas)
            BuildConnectedAreas_r(tr_local.tr.viewDef.areaNum)
        }

        fun FindViewLightsAndEntities() {
            // clear the visible lightDef and entityDef lists
            tr_local.tr.viewDef.viewLights = null
            tr_local.tr.viewDef.viewEntitys = null
            tr_local.tr.viewDef.numViewEntitys = 0

            // find the area to start the portal flooding in
            if (!RenderSystem_init.r_usePortals.GetBool()) {
                // debug tool to force no portal culling
                tr_local.tr.viewDef.areaNum = -1
            } else {
                tr_local.tr.viewDef.areaNum = PointInArea(tr_local.tr.viewDef.initialViewAreaOrigin)
            }

            // determine all possible connected areas for
            // light-behind-door culling
            BuildConnectedAreas()

            // bump the view count, invalidating all
            // visible areas
            tr_local.tr.viewCount++
            //            System.out.println("tr.viewCount::FindViewLightsAndEntities");
            tr_local.tr.DBG_viewCount++

            // flow through all the portals and add models / lights
            if (RenderSystem_init.r_singleArea.GetBool()) {
                // if debugging, only mark this area
                // if we are outside the world, don't draw anything
                if (tr_local.tr.viewDef.areaNum >= 0) {
                    val ps = portalStack_s()
                    var i: Int
                    if (tr_local.tr.viewDef.areaNum != lastPrintedAreaNum) {
                        lastPrintedAreaNum = tr_local.tr.viewDef.areaNum
                        Common.common.Printf("entering portal area %d\n", tr_local.tr.viewDef.areaNum)
                    }
                    i = 0
                    while (i < 5) {
                        ps.portalPlanes[i] = idPlane(tr_local.tr.viewDef.frustum[i])
                        i++
                    }
                    ps.numPortalPlanes = 5
                    ps.rect = idScreenRect(tr_local.tr.viewDef.scissor)
                    AddAreaRefs(tr_local.tr.viewDef.areaNum, ps)
                }
            } else {
                // note that the center of projection for flowing through portals may
                // be a different point than initialViewAreaOrigin for subviews that
                // may have the viewOrigin in a solid/invalid area
                FlowViewThroughPortals(tr_local.tr.viewDef.renderView.vieworg, 5, tr_local.tr.viewDef.frustum)
            }
        }

        override fun NumPortals(): Int {
            return numInterAreaPortals
        }

        /*
         ==============
         FindPortal

         Game code uses this to identify which portals are inside doors.
         Returns 0 if no portal contacts the bounds
         ==============
         */
        override fun  /*qhandle_t*/FindPortal(b: idBounds?): Int {
            var i: Int
            var j: Int
            val wb = idBounds()
            var portal: doublePortal_s?
            var w: idWinding?
            i = 0
            while (i < numInterAreaPortals) {
                portal = doublePortals.get(i)
                w = portal.portals.get(0).w
                wb.Clear()
                j = 0
                while (j < w.GetNumPoints()) {
                    wb.AddPoint(w.oGet(j).ToVec3())
                    j++
                }
                if (wb.IntersectsBounds(b)) {
                    return i + 1
                }
                i++
            }
            return 0
        }

        /*
         ==============
         SetPortalState

         doors explicitly close off portals when shut
         ==============
         */
        override fun SetPortalState( /*qhandle_t*/
            portal: Int, blockTypes: Int
        ) {
            if (portal == 0) {
                return
            }
            if (portal < 1 || portal > numInterAreaPortals) {
                Common.common.Error("SetPortalState: bad portal number %d", portal)
            }
            val old = doublePortals.get(portal - 1).blockingBits
            if (old == blockTypes) {
                return
            }
            doublePortals.get(portal - 1).blockingBits = blockTypes

            // leave the connectedAreaGroup the same on one side,
            // then flood fill from the other side with a new number for each changed attribute
            for (i in 0 until RenderWorld.NUM_PORTAL_ATTRIBUTES) {
                if (old xor blockTypes and (1 shl i) != 0) {
                    connectedAreaNum++
                    FloodConnectedAreas(portalAreas.get(doublePortals.get(portal - 1).portals.get(1).intoArea), i)
                }
            }
            if (Session.Companion.session.writeDemo != null) {
                Session.Companion.session.writeDemo.WriteInt(demoSystem_t.DS_RENDER)
                Session.Companion.session.writeDemo.WriteInt(demoCommand_t.DC_SET_PORTAL_STATE)
                Session.Companion.session.writeDemo.WriteInt(portal)
                Session.Companion.session.writeDemo.WriteInt(blockTypes)
            }
        }

        override fun GetPortalState(   /*qhandle_t */portal: Int): Int {
            if (portal == 0) {
                return 0
            }
            if (portal < 1 || portal > numInterAreaPortals) {
                Common.common.Error("GetPortalState: bad portal number %d", portal)
            }
            return doublePortals.get(portal - 1).blockingBits
        }

        override fun AreasAreConnected(areaNum1: Int, areaNum2: Int, connection: portalConnection_t?): Boolean {
            if (areaNum1 == -1 || areaNum2 == -1) {
                return false
            }
            if (areaNum1 > numPortalAreas || areaNum2 > numPortalAreas || areaNum1 < 0 || areaNum2 < 0) {
                Common.common.Error("idRenderWorldLocal::AreAreasConnected: bad parms: %d, %d", areaNum1, areaNum2)
            }
            var attribute = 0
            var intConnection = connection.ordinal
            while (intConnection > 1) {
                attribute++
                intConnection = intConnection shr 1
            }
            if (attribute >= RenderWorld.NUM_PORTAL_ATTRIBUTES || 1 shl attribute != connection.ordinal) {
                Common.common.Error(
                    "idRenderWorldLocal::AreasAreConnected: bad connection number: %d\n",
                    connection.ordinal
                )
            }
            return portalAreas.get(areaNum1).connectedAreaNum.get(attribute) == portalAreas.get(areaNum2).connectedAreaNum.get(
                attribute
            )
        }

        fun FloodConnectedAreas(area: portalArea_s?, portalAttributeIndex: Int) {
            if (area.connectedAreaNum.get(portalAttributeIndex) == connectedAreaNum) {
                return
            }
            area.connectedAreaNum.get(portalAttributeIndex) = connectedAreaNum
            var p = area.portals
            while (p != null) {
                if (0 == p.doublePortal.blockingBits and (1 shl portalAttributeIndex)) {
                    FloodConnectedAreas(portalAreas.get(p.intoArea), portalAttributeIndex)
                }
                p = p.next
            }
        }

        fun GetAreaScreenRect(areaNum: Int): idScreenRect? {
            return areaScreenRect.get(areaNum)
        }

        /*
         =====================
         idRenderWorldLocal::ShowPortals

         Debugging tool, won't work correctly with SMP or when mirrors are present
         =====================
         */
        fun ShowPortals() {
            var i: Int
            var j: Int
            var area: portalArea_s?
            var p: portal_s?
            var w: idWinding?

            // flood out through portals, setting area viewCount
            i = 0
            while (i < numPortalAreas) {
                area = portalAreas.get(i)
                if (area.viewCount != tr_local.tr.viewCount) {
                    i++
                    continue
                }
                p = area.portals
                while (p != null) {
                    w = p.w
                    if (null == w) {
                        p = p.next
                        continue
                    }
                    if (portalAreas.get(p.intoArea).viewCount != tr_local.tr.viewCount) {
                        // red = can't see
                        qgl.qglColor3f(1f, 0f, 0f)
                    } else {
                        // green = see through
                        qgl.qglColor3f(0f, 1f, 0f)
                    }
                    qgl.qglBegin(GL11.GL_LINE_LOOP)
                    j = 0
                    while (j < w.GetNumPoints()) {
                        qgl.qglVertex3fv(w.oGet(j).ToFloatPtr())
                        j++
                    }
                    qgl.qglEnd()
                    p = p.next
                }
                i++
            }
        }

        //===============================================================================================================
        // RenderWorld_demo.cpp
        override fun StartWritingDemo(demo: idDemoFile?) {
            var i: Int

            // FIXME: we should track the idDemoFile locally, instead of snooping into session for it
            WriteLoadMap()

            // write the door portal state
            i = 0
            while (i < numInterAreaPortals) {
                if (doublePortals.get(i).blockingBits != 0) {
                    SetPortalState(i + 1, doublePortals.get(i).blockingBits)
                }
                i++
            }

            // clear the archive counter on all defs
            i = 0
            while (i < lightDefs.Num()) {
                if (lightDefs.oGet(i) != null) {
                    lightDefs.oGet(i).archived = false
                }
                i++
            }
            i = 0
            while (i < entityDefs.Num()) {
                if (entityDefs.oGet(i) != null) {
                    entityDefs.oGet(i).archived = false
                }
                i++
            }
        }

        override fun StopWritingDemo() {
            //	writeDemo = NULL;
        }

        override fun ProcessDemoCommand(
            readDemo: idDemoFile?,
            renderView: renderView_s?,
            demoTimeOffset: CInt?
        ): Boolean {
            var newMap = false
            val viewShadow = renderViewShadow()
            if (null == readDemo) {
                return false
            }
            val dc: demoCommand_t
            val d = CInt()
            val h = CInt()
            if (TempDump.NOT(readDemo.ReadInt(d).toDouble())) {
                // a demoShot may not have an endFrame, but it is still valid
                return false
            }
            dc = demoCommand_t.values()[d.getVal()]
            when (dc) {
                demoCommand_t.DC_LOADMAP -> {
                    // read the initial data
                    val header = demoHeader_t()
                    readDemo.ReadInt(header.version)
                    readDemo.ReadInt(header.sizeofRenderEntity)
                    readDemo.ReadInt(header.sizeofRenderLight)
                    var i = 0
                    while (i < 256) {
                        val c = shortArrayOf(0)
                        readDemo.ReadChar(c)
                        header.mapname[i] = c[0] as Char
                        i++
                    }
                    // the internal version value got replaced by DS_VERSION at toplevel
                    if (header.version.getVal() != 4) {
                        Common.common.Error("Demo version mismatch.\n")
                    }
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_LOADMAP: %s\n", *header.mapname)
                    }
                    InitFromMap(TempDump.ctos(header.mapname))
                    newMap = true // we will need to set demoTimeOffset
                }
                demoCommand_t.DC_RENDERVIEW -> {
                    readDemo.ReadInt(viewShadow.viewID)
                    readDemo.ReadInt(viewShadow.x)
                    readDemo.ReadInt(viewShadow.y)
                    readDemo.ReadInt(viewShadow.width)
                    readDemo.ReadInt(viewShadow.height)
                    readDemo.ReadFloat(viewShadow.fov_x)
                    readDemo.ReadFloat(viewShadow.fov_y)
                    readDemo.ReadVec3(viewShadow.vieworg)
                    readDemo.ReadMat3(viewShadow.viewaxis)
                    readDemo.ReadBool(viewShadow.cramZNear)
                    readDemo.ReadBool(viewShadow.forceUpdate)
                    // binary compatibility with win32 padded structures
                    val tmp = ShortArray(1)
                    readDemo.ReadChar(tmp)
                    readDemo.ReadChar(tmp)
                    readDemo.ReadInt(viewShadow.time)
                    var i = 0
                    while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                        readDemo.ReadFloat(viewShadow.shaderParms[i])
                        i++
                    }

//                    if (!readDemo.ReadInt(viewShadow.globalMaterial)) {
                    if (TempDump.NOT(readDemo.Read(viewShadow.globalMaterial).toDouble())) {
                        return false
                    }
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_RENDERVIEW: %d\n", viewShadow.time)
                    }

                    // possibly change the time offset if this is from a new map
                    if (newMap && demoTimeOffset.getVal() != 0) {
                        demoTimeOffset.setVal(viewShadow.time.getVal() - EventLoop.eventLoop.Milliseconds())
                    }
                    renderView.atomicSet(viewShadow)
                    return false
                }
                demoCommand_t.DC_UPDATE_ENTITYDEF -> ReadRenderEntity()
                demoCommand_t.DC_DELETE_ENTITYDEF -> {
                    if (TempDump.NOT(readDemo.ReadInt(h).toDouble())) {
                        return false
                    }
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_DELETE_ENTITYDEF: %d\n", h.getVal())
                    }
                    FreeEntityDef(h.getVal())
                }
                demoCommand_t.DC_UPDATE_LIGHTDEF -> ReadRenderLight()
                demoCommand_t.DC_DELETE_LIGHTDEF -> {
                    if (TempDump.NOT(readDemo.ReadInt(h).toDouble())) {
                        return false
                    }
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_DELETE_LIGHTDEF: %d\n", h.getVal())
                    }
                    FreeLightDef(h.getVal())
                }
                demoCommand_t.DC_CAPTURE_RENDER -> {
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_CAPTURE_RENDER\n")
                    }
                    RenderSystem.renderSystem.CaptureRenderToImage(readDemo.ReadHashString())
                }
                demoCommand_t.DC_CROP_RENDER -> {
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_CROP_RENDER\n")
                    }
                    val size = Stream.generate { CInt() }.limit(3).toArray<CInt?> { _Dummy_.__Array__() }
                    readDemo.ReadInt(size[0])
                    readDemo.ReadInt(size[1])
                    readDemo.ReadInt(size[2])
                    RenderSystem.renderSystem.CropRenderSize(size[0].getVal(), size[1].getVal(), size[2].getVal() != 0)
                }
                demoCommand_t.DC_UNCROP_RENDER -> {
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_UNCROP\n")
                    }
                    RenderSystem.renderSystem.UnCrop()
                }
                demoCommand_t.DC_GUI_MODEL -> {
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_GUI_MODEL\n")
                    }
                    tr_local.tr.demoGuiModel.ReadFromDemo(readDemo)
                }
                demoCommand_t.DC_DEFINE_MODEL -> {
                    val model = ModelManager.renderModelManager.AllocModel()
                    model.ReadFromDemoFile(Session.Companion.session.readDemo)
                    // add to model manager, so we can find it
                    ModelManager.renderModelManager.AddModel(model)

                    // save it in the list to free when clearing this map
                    localModels.Append(model)
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_DEFINE_MODEL\n")
                    }
                }
                demoCommand_t.DC_SET_PORTAL_STATE -> {
                    val data = Stream.generate { CInt() }.limit(2).toArray<CInt?> { _Dummy_.__Array__() }
                    readDemo.ReadInt(data[0])
                    readDemo.ReadInt(data[1])
                    SetPortalState(data[0].getVal(), data[1].getVal())
                    if (RenderSystem_init.r_showDemo.GetBool()) {
                        Common.common.Printf("DC_SET_PORTAL_STATE: %d %d\n", data[0].getVal(), data[1].getVal())
                    }
                }
                demoCommand_t.DC_END_FRAME -> return true
                else -> Common.common.Error("Bad token in demo stream")
            }
            return false
        }

        fun WriteLoadMap() {

            // only the main renderWorld writes stuff to demos, not the wipes or
            // menu renders
            if (this !== Session.Companion.session.rw) {
                return
            }
            Session.Companion.session.writeDemo.WriteInt(demoSystem_t.DS_RENDER)
            Session.Companion.session.writeDemo.WriteInt(demoCommand_t.DC_LOADMAP)
            val header = demoHeader_t()
            //            strncpy(header.mapname, mapName.c_str(), sizeof(header.mapname) - 1);
            header.mapname = mapName.c_str()
            header.version.setVal(4)
            header.sizeofRenderEntity.setVal(4)
            header.sizeofRenderLight.setVal(4)
            Session.Companion.session.writeDemo.WriteInt(header.version.getVal())
            Session.Companion.session.writeDemo.WriteInt(header.sizeofRenderEntity.getVal())
            Session.Companion.session.writeDemo.WriteInt(header.sizeofRenderLight.getVal())
            for (i in 0..255) {
                Session.Companion.session.writeDemo.WriteChar(header.mapname[i] as Short)
            }
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf("write DC_DELETE_LIGHTDEF: %s\n", mapName)
            }
        }

        fun WriteRenderView(renderView: renderView_s?) {
            var i: Int

            // only the main renderWorld writes stuff to demos, not the wipes or
            // menu renders
            if (this !== Session.Companion.session.rw) {
                return
            }

            // write the actual view command
            Session.Companion.session.writeDemo.WriteInt(demoSystem_t.DS_RENDER)
            Session.Companion.session.writeDemo.WriteInt(demoCommand_t.DC_RENDERVIEW)
            Session.Companion.session.writeDemo.WriteInt(renderView.viewID)
            Session.Companion.session.writeDemo.WriteInt(renderView.x)
            Session.Companion.session.writeDemo.WriteInt(renderView.y)
            Session.Companion.session.writeDemo.WriteInt(renderView.width)
            Session.Companion.session.writeDemo.WriteInt(renderView.height)
            Session.Companion.session.writeDemo.WriteFloat(renderView.fov_x)
            Session.Companion.session.writeDemo.WriteFloat(renderView.fov_y)
            Session.Companion.session.writeDemo.WriteVec3(renderView.vieworg)
            Session.Companion.session.writeDemo.WriteMat3(renderView.viewaxis)
            Session.Companion.session.writeDemo.WriteBool(renderView.cramZNear)
            Session.Companion.session.writeDemo.WriteBool(renderView.forceUpdate)
            // binary compatibility with old win32 version writing padded structures directly to disk
            Session.Companion.session.writeDemo.WriteUnsignedChar(0.toChar())
            Session.Companion.session.writeDemo.WriteUnsignedChar(0.toChar())
            Session.Companion.session.writeDemo.WriteInt(renderView.time)
            i = 0
            while (i < RenderWorld.MAX_GLOBAL_SHADER_PARMS) {
                Session.Companion.session.writeDemo.WriteFloat(renderView.shaderParms[i])
                i++
            }
            //            session.writeDemo.WriteInt(renderView.globalMaterial);
            Session.Companion.session.writeDemo.Write(renderView.globalMaterial)
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf("write DC_RENDERVIEW: %d\n", renderView.time)
            }
        }

        fun WriteVisibleDefs(viewDef: viewDef_s?) {
            // only the main renderWorld writes stuff to demos, not the wipes or
            // menu renders
            if (this !== Session.Companion.session.rw) {
                return
            }

            // make sure all necessary entities and lights are updated
            var viewEnt = viewDef.viewEntitys
            while (viewEnt != null) {
                val ent = viewEnt.entityDef
                if (ent.archived) {
                    // still up to date
                    viewEnt = viewEnt.next
                    continue
                }

                // write it out
                WriteRenderEntity(ent.index, ent.parms)
                ent.archived = true
                viewEnt = viewEnt.next
            }
            var viewLight = viewDef.viewLights
            while (viewLight != null) {
                val light = viewLight.lightDef
                if (light.archived) {
                    // still up to date
                    viewLight = viewLight.next
                    continue
                }
                // write it out
                WriteRenderLight(light.index, light.parms)
                light.archived = true
                viewLight = viewLight.next
            }
        }

        fun WriteFreeLight(   /*qhandle_t*/handle: Int) {

            // only the main renderWorld writes stuff to demos, not the wipes or
            // menu renders
            if (this !== Session.Companion.session.rw) {
                return
            }
            Session.Companion.session.writeDemo.WriteInt(demoSystem_t.DS_RENDER.ordinal)
            Session.Companion.session.writeDemo.WriteInt(demoCommand_t.DC_DELETE_LIGHTDEF)
            Session.Companion.session.writeDemo.WriteInt(handle)
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf("write DC_DELETE_LIGHTDEF: %d\n", handle)
            }
        }

        fun WriteFreeEntity(   /*qhandle_t*/handle: Int) {

            // only the main renderWorld writes stuff to demos, not the wipes or
            // menu renders
            if (this !== Session.Companion.session.rw) {
                return
            }
            Session.Companion.session.writeDemo.WriteInt(demoSystem_t.DS_RENDER.ordinal)
            Session.Companion.session.writeDemo.WriteInt(demoCommand_t.DC_DELETE_ENTITYDEF)
            Session.Companion.session.writeDemo.WriteInt(handle)
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf("write DC_DELETE_ENTITYDEF: %d\n", handle)
            }
        }

        fun WriteRenderLight(   /*qhandle_t*/handle: Int, light: renderLight_s?) {

            // only the main renderWorld writes stuff to demos, not the wipes or
            // menu renders
            if (this !== Session.Companion.session.rw) {
                return
            }
            Session.Companion.session.writeDemo.WriteInt(demoSystem_t.DS_RENDER.ordinal)
            Session.Companion.session.writeDemo.WriteInt(demoCommand_t.DC_UPDATE_LIGHTDEF)
            Session.Companion.session.writeDemo.WriteInt(handle)
            Session.Companion.session.writeDemo.WriteMat3(light.axis)
            Session.Companion.session.writeDemo.WriteVec3(light.origin)
            Session.Companion.session.writeDemo.WriteInt(light.suppressLightInViewID)
            Session.Companion.session.writeDemo.WriteInt(light.allowLightInViewID)
            Session.Companion.session.writeDemo.WriteBool(light.noShadows)
            Session.Companion.session.writeDemo.WriteBool(light.noSpecular)
            Session.Companion.session.writeDemo.WriteBool(light.pointLight)
            Session.Companion.session.writeDemo.WriteBool(light.parallel)
            Session.Companion.session.writeDemo.WriteVec3(light.lightRadius)
            Session.Companion.session.writeDemo.WriteVec3(light.lightCenter)
            Session.Companion.session.writeDemo.WriteVec3(light.target)
            Session.Companion.session.writeDemo.WriteVec3(light.right)
            Session.Companion.session.writeDemo.WriteVec3(light.up)
            Session.Companion.session.writeDemo.WriteVec3(light.start)
            Session.Companion.session.writeDemo.WriteVec3(light.end)
            //            session.writeDemo.WriteInt(light.prelightModel);
            Session.Companion.session.writeDemo.Write(light.prelightModel)
            Session.Companion.session.writeDemo.WriteInt(light.lightId)
            //            session.writeDemo.WriteInt(light.shader);
            Session.Companion.session.writeDemo.Write(light.shader)
            for (i in 0 until Material.MAX_ENTITY_SHADER_PARMS) {
                Session.Companion.session.writeDemo.WriteFloat(light.shaderParms[i])
            }
            //            session.writeDemo.WriteInt(light.referenceSound);
            Session.Companion.session.writeDemo.Write(light.referenceSound)
            if (light.prelightModel != null) {
                Session.Companion.session.writeDemo.WriteHashString(light.prelightModel.Name())
            }
            if (light.shader != null) {
                Session.Companion.session.writeDemo.WriteHashString(light.shader.GetName())
            }
            if (light.referenceSound != null) {
                val index = light.referenceSound.Index()
                Session.Companion.session.writeDemo.WriteInt(index)
            }
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf("write DC_UPDATE_LIGHTDEF: %d\n", handle)
            }
        }

        fun WriteRenderEntity(   /*qhandle_t*/handle: Int, ent: renderEntity_s?) {

            // only the main renderWorld writes stuff to demos, not the wipes or
            // menu renders
            if (this !== Session.Companion.session.rw) {
                return
            }
            Session.Companion.session.writeDemo.WriteInt(demoSystem_t.DS_RENDER.ordinal)
            Session.Companion.session.writeDemo.WriteInt(demoCommand_t.DC_UPDATE_ENTITYDEF)
            Session.Companion.session.writeDemo.WriteInt(handle)

//            session.writeDemo.WriteInt((int) ent.hModel);
            Session.Companion.session.writeDemo.Write(ent.hModel)
            Session.Companion.session.writeDemo.WriteInt(ent.entityNum)
            Session.Companion.session.writeDemo.WriteInt(ent.bodyId)
            Session.Companion.session.writeDemo.WriteVec3(ent.bounds.oGet(0))
            Session.Companion.session.writeDemo.WriteVec3(ent.bounds.oGet(1))
            //            session.writeDemo.WriteInt((int) ent.callback);
            Session.Companion.session.writeDemo.Write(ent.callback)
            //            session.writeDemo.WriteInt((int) ent.callbackData);
            Session.Companion.session.writeDemo.Write(ent.callbackData)
            Session.Companion.session.writeDemo.WriteInt(ent.suppressSurfaceInViewID)
            Session.Companion.session.writeDemo.WriteInt(ent.suppressShadowInViewID)
            Session.Companion.session.writeDemo.WriteInt(ent.suppressShadowInLightID)
            Session.Companion.session.writeDemo.WriteInt(ent.allowSurfaceInViewID)
            Session.Companion.session.writeDemo.WriteVec3(ent.origin)
            Session.Companion.session.writeDemo.WriteMat3(ent.axis)
            //            session.writeDemo.WriteInt((int) ent.customShader);
//            session.writeDemo.WriteInt((int) ent.referenceShader);
//            session.writeDemo.WriteInt((int) ent.customSkin);
//            session.writeDemo.WriteInt((int) ent.referenceSound);
            Session.Companion.session.writeDemo.Write(ent.customShader)
            Session.Companion.session.writeDemo.Write(ent.referenceShader)
            Session.Companion.session.writeDemo.Write(ent.customSkin)
            Session.Companion.session.writeDemo.Write(ent.referenceSound)
            for (i in 0 until Material.MAX_ENTITY_SHADER_PARMS) {
                Session.Companion.session.writeDemo.WriteFloat(ent.shaderParms[i])
            }
            for (i in 0 until RenderWorld.MAX_RENDERENTITY_GUI) {
//                session.writeDemo.WriteInt((int &) ent.gui[i]);
                Session.Companion.session.writeDemo.Write(ent.gui[i])
            }
            //            session.writeDemo.WriteInt((int) ent.remoteRenderView);
            Session.Companion.session.writeDemo.Write(ent.remoteRenderView)
            Session.Companion.session.writeDemo.WriteInt(ent.numJoints)
            //            session.writeDemo.WriteInt((int) ent.joints);
            for (joint in ent.joints) { //TODO: double check if writing individual floats is equavalent to the int cast above.
                val mat = joint.ToFloatPtr()
                val buffer = ByteBuffer.allocate(mat.size * 4)
                buffer.asFloatBuffer().put(mat)
                Session.Companion.session.readDemo.Write(buffer)
                //                for (int a = 0; a < mat.length; a++) {
//                    session.readDemo.WriteFloat(mat[a]);
//                }
            }
            Session.Companion.session.writeDemo.WriteFloat(ent.modelDepthHack)
            Session.Companion.session.writeDemo.WriteBool(ent.noSelfShadow)
            Session.Companion.session.writeDemo.WriteBool(ent.noShadow)
            Session.Companion.session.writeDemo.WriteBool(ent.noDynamicInteractions)
            Session.Companion.session.writeDemo.WriteBool(ent.weaponDepthHack)
            Session.Companion.session.writeDemo.WriteInt(ent.forceUpdate)
            if (ent.customShader != null) {
                Session.Companion.session.writeDemo.WriteHashString(ent.customShader.GetName())
            }
            if (ent.customSkin != null) {
                Session.Companion.session.writeDemo.WriteHashString(ent.customSkin.GetName())
            }
            if (ent.hModel != null) {
                Session.Companion.session.writeDemo.WriteHashString(ent.hModel.Name())
            }
            if (ent.referenceShader != null) {
                Session.Companion.session.writeDemo.WriteHashString(ent.referenceShader.GetName())
            }
            if (ent.referenceSound != null) {
                val index = ent.referenceSound.Index()
                Session.Companion.session.writeDemo.WriteInt(index)
            }
            if (ent.numJoints != 0) {
                for (i in 0 until ent.numJoints) {
                    val data = ent.joints[i].ToFloatPtr()
                    for (j in 0..11) {
                        Session.Companion.session.writeDemo.WriteFloat(data[j])
                    }
                }
            }

            /*
             if ( ent.decals ) {
             ent.decals.WriteToDemoFile( session.readDemo );
             }
             if ( ent.overlay ) {
             ent.overlay.WriteToDemoFile( session.writeDemo );
             }
             */if (RenderWorld_local.WRITE_GUIS) {
//                if (ent.gui != null) {
//                    ent.gui.WriteToDemoFile(session.writeDemo);
//                }
//                if (ent.gui2 != null) {
//                    ent.gui2.WriteToDemoFile(session.writeDemo);
//                }
//                if (ent.gui3 != null) {
//                    ent.gui3.WriteToDemoFile(session.writeDemo);
//                }
            }

            // RENDERDEMO_VERSION >= 2 ( Doom3 1.2 )
            Session.Companion.session.writeDemo.WriteInt(ent.timeGroup)
            Session.Companion.session.writeDemo.WriteInt(ent.xrayIndex)
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf(
                    "write DC_UPDATE_ENTITYDEF: %d = %s\n",
                    handle,
                    if (ent.hModel != null) ent.hModel.Name() else "NULL"
                )
            }
        }

        fun ReadRenderEntity() {
            val ent = renderEntity_s()
            val shadow = renderEntityShadow()
            val index = CInt()
            var i: Int
            Session.Companion.session.readDemo.ReadInt(index)
            if (index.getVal() < 0) {
                Common.common.Error("ReadRenderEntity: index < 0")
            }

//            session.readDemo.ReadInt((int) shadow.hModel);
            Session.Companion.session.readDemo.Read(shadow.hModel)
            Session.Companion.session.readDemo.ReadInt(shadow.entityNum)
            Session.Companion.session.readDemo.ReadInt(shadow.bodyId)
            Session.Companion.session.readDemo.ReadVec3(shadow.bounds.oGet(0))
            Session.Companion.session.readDemo.ReadVec3(shadow.bounds.oGet(1))
            //            session.readDemo.ReadInt((int) shadow.callback);
//            session.readDemo.ReadInt((int) shadow.callbackData);
            Session.Companion.session.readDemo.Read(shadow.callback)
            Session.Companion.session.readDemo.Read(shadow.callbackData)
            Session.Companion.session.readDemo.ReadInt(shadow.suppressSurfaceInViewID)
            Session.Companion.session.readDemo.ReadInt(shadow.suppressShadowInViewID)
            Session.Companion.session.readDemo.ReadInt(shadow.suppressShadowInLightID)
            Session.Companion.session.readDemo.ReadInt(shadow.allowSurfaceInViewID)
            Session.Companion.session.readDemo.ReadVec3(shadow.origin)
            Session.Companion.session.readDemo.ReadMat3(shadow.axis)
            //            session.readDemo.ReadInt((int) shadow.customShader);
//            session.readDemo.ReadInt((int) shadow.referenceShader);
//            session.readDemo.ReadInt((int) shadow.customSkin);
//            session.readDemo.ReadInt((int) shadow.referenceSound);
            Session.Companion.session.readDemo.Read(shadow.customShader)
            Session.Companion.session.readDemo.Read(shadow.referenceShader)
            Session.Companion.session.readDemo.Read(shadow.customSkin)
            Session.Companion.session.readDemo.Read(shadow.referenceSound)
            i = 0
            while (i < Material.MAX_ENTITY_SHADER_PARMS) {
                Session.Companion.session.readDemo.ReadFloat(shadow.shaderParms[i])
                i++
            }
            i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {

//                session.readDemo.ReadInt((int) shadow.gui[i]);
                Session.Companion.session.readDemo.Read(shadow.gui[i])
                i++
            }
            //            session.readDemo.ReadInt((int) shadow.remoteRenderView);
            Session.Companion.session.readDemo.Read(shadow.remoteRenderView)
            Session.Companion.session.readDemo.ReadInt(shadow.numJoints)
            //            session.readDemo.ReadInt((int) shadow.joints);
            for (joint in shadow.joints) { //TODO: double check if writing individual floats is equavalent to the int cast above.
                val mat = joint.ToFloatPtr()
                val buffer = ByteBuffer.allocate(mat.size * 4)
                buffer.asFloatBuffer().put(mat)
                Session.Companion.session.readDemo.Read(buffer)
                //                for (int a = 0; a < mat.length; a++) {
//                    float[] b = {0};
//                    session.readDemo.ReadFloat(b);
//                    mat[a] = b[0];
//                }
            }
            Session.Companion.session.readDemo.ReadFloat(shadow.modelDepthHack)
            Session.Companion.session.readDemo.ReadBool(shadow.noSelfShadow)
            Session.Companion.session.readDemo.ReadBool(shadow.noShadow)
            Session.Companion.session.readDemo.ReadBool(shadow.noDynamicInteractions)
            Session.Companion.session.readDemo.ReadBool(shadow.weaponDepthHack)
            Session.Companion.session.readDemo.ReadInt(shadow.forceUpdate)
            shadow.callback = null
            if (shadow.customShader != null) {
                shadow.customShader =
                    DeclManager.declManager.FindMaterial(Session.Companion.session.readDemo.ReadHashString())
            }
            if (shadow.customSkin != null) {
                shadow.customSkin =
                    DeclManager.declManager.FindSkin(Session.Companion.session.readDemo.ReadHashString())
            }
            if (shadow.hModel != null) {
                shadow.hModel =
                    ModelManager.renderModelManager.FindModel(Session.Companion.session.readDemo.ReadHashString())
            }
            if (shadow.referenceShader != null) {
                shadow.referenceShader =
                    DeclManager.declManager.FindMaterial(Session.Companion.session.readDemo.ReadHashString())
            }
            if (shadow.referenceSound != null) {
//		int	index;
                Session.Companion.session.readDemo.ReadInt(index)
                shadow.referenceSound = Session.Companion.session.sw.EmitterForIndex(index.getVal())
            }
            if (shadow.numJoints.getVal() != 0) {
                shadow.joints = arrayOfNulls(shadow.numJoints.getVal()) //Mem_Alloc16(ent.numJoints);
                i = 0
                while (i < shadow.numJoints.getVal()) {
                    val data = shadow.joints[i].ToFloatPtr()
                    for (j in 0..11) {
                        val d = CFloat()
                        Session.Companion.session.readDemo.ReadFloat(d)
                        data[j] = d.getVal()
                    }
                    i++
                }
            }
            shadow.callbackData = null

            /*
             if ( ent.decals ) {
             ent.decals = idRenderModelDecal::Alloc();
             ent.decals.ReadFromDemoFile( session.readDemo );
             }
             if ( ent.overlay ) {
             ent.overlay = idRenderModelOverlay::Alloc();
             ent.overlay.ReadFromDemoFile( session.readDemo );
             }
             */i = 0
            while (i < RenderWorld.MAX_RENDERENTITY_GUI) {
                if (shadow.gui[i] != null) {
                    shadow.gui[i] = UserInterface.uiManager.Alloc()
                    if (RenderWorld_local.WRITE_GUIS) {
                        shadow.gui[i].ReadFromDemoFile(Session.Companion.session.readDemo)
                    }
                }
                i++
            }

            // >= Doom3 v1.2 only
            if (Session.Companion.session.renderdemoVersion >= 2) {
                Session.Companion.session.readDemo.ReadInt(shadow.timeGroup)
                Session.Companion.session.readDemo.ReadInt(shadow.xrayIndex)
            } else {
                shadow.timeGroup.setVal(0)
                shadow.xrayIndex.setVal(0)
            }
            ent.atomicSet(shadow)
            UpdateEntityDef(index.getVal(), ent)
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf(
                    "DC_UPDATE_ENTITYDEF: %d = %s\n",
                    index.getVal(),
                    if (shadow.hModel != null) shadow.hModel.Name() else "NULL"
                )
            }
        }

        fun ReadRenderLight() {
            val shadow = renderLightShadow()
            val light = renderLight_s()
            val index = CInt()
            Session.Companion.session.readDemo.ReadInt(index)
            if (index.getVal() < 0) {
                Common.common.Error("ReadRenderLight: index < 0 ")
            }
            Session.Companion.session.readDemo.ReadMat3(shadow.axis)
            Session.Companion.session.readDemo.ReadVec3(shadow.origin)
            Session.Companion.session.readDemo.ReadInt(shadow.suppressLightInViewID)
            Session.Companion.session.readDemo.ReadInt(shadow.allowLightInViewID)
            Session.Companion.session.readDemo.ReadBool(shadow.noShadows)
            Session.Companion.session.readDemo.ReadBool(shadow.noSpecular)
            Session.Companion.session.readDemo.ReadBool(shadow.pointLight)
            Session.Companion.session.readDemo.ReadBool(shadow.parallel)
            Session.Companion.session.readDemo.ReadVec3(shadow.lightRadius)
            Session.Companion.session.readDemo.ReadVec3(shadow.lightCenter)
            Session.Companion.session.readDemo.ReadVec3(shadow.target)
            Session.Companion.session.readDemo.ReadVec3(shadow.right)
            Session.Companion.session.readDemo.ReadVec3(shadow.up)
            Session.Companion.session.readDemo.ReadVec3(shadow.start)
            Session.Companion.session.readDemo.ReadVec3(shadow.end)
            //            session.readDemo.ReadInt((int) shadow.prelightModel);
            Session.Companion.session.readDemo.Read(shadow.prelightModel)
            Session.Companion.session.readDemo.ReadInt(shadow.lightId)
            //            session.readDemo.ReadInt((int) shadow.shader);
            Session.Companion.session.readDemo.Read(shadow.shader)
            for (i in 0 until Material.MAX_ENTITY_SHADER_PARMS) {
                val parm = CFloat()
                Session.Companion.session.readDemo.ReadFloat(parm)
                shadow.shaderParms[i] = parm.getVal()
            }
            //            session.readDemo.ReadInt((int) shadow.referenceSound);
            Session.Companion.session.readDemo.Read(shadow.referenceSound)
            if (shadow.prelightModel != null) {
                shadow.prelightModel =
                    ModelManager.renderModelManager.FindModel(Session.Companion.session.readDemo.ReadHashString())
            }
            if (shadow.shader != null) {
                shadow.shader =
                    DeclManager.declManager.FindMaterial(Session.Companion.session.readDemo.ReadHashString())
            }
            if (shadow.referenceSound != null) {
//		int	index;
                Session.Companion.session.readDemo.ReadInt(index)
                shadow.referenceSound = Session.Companion.session.sw.EmitterForIndex(index.getVal())
            }
            light.atomicSet(shadow)
            UpdateLightDef(index.getVal(), light)
            if (RenderSystem_init.r_showDemo.GetBool()) {
                Common.common.Printf("DC_UPDATE_LIGHTDEF: %d\n", index.getVal())
            }
        }

        //--------------------------
        // RenderWorld.cpp
        fun ResizeInteractionTable() {
            // we overflowed the interaction table, so dump it
            // we may want to resize this in the future if it turns out to be common
            Common.common.Printf("idRenderWorldLocal::ResizeInteractionTable: overflowed interactionTableWidth, dumping\n")
            //            R_StaticFree(interactionTable);
            interactionTable = null
        }

        fun AddEntityRefToArea(def: idRenderEntityLocal?, area: portalArea_s?) {
            val ref: areaReference_s
            if (TempDump.NOT(def)) {
                Common.common.Error("idRenderWorldLocal::AddEntityRefToArea: NULL def")
            }
            ref = areaReference_s() //areaReferenceAllocator.Alloc();
            tr_local.tr.pc.c_entityReferences++
            ref.entity = def

            // link to entityDef
            ref.ownerNext = def.entityRefs
            def.entityRefs = ref

            // link to end of area list
            ref.area = area
            ref.areaNext = area.entityRefs
            ref.areaPrev = area.entityRefs.areaPrev
            ref.areaNext.areaPrev = ref
            ref.areaPrev.areaNext = ref
        }

        fun AddLightRefToArea(light: idRenderLightLocal?, area: portalArea_s?) {
            val lref: areaReference_s

            // add a lightref to this area
            lref = areaReference_s() //areaReferenceAllocator.Alloc();
            lref.light = light
            lref.area = area
            lref.ownerNext = light.references
            light.references = lref
            tr_local.tr.pc.c_lightReferences++

            // doubly linked list so we can free them easily later
            area.lightRefs.areaNext.areaPrev = lref
            lref.areaNext = area.lightRefs.areaNext
            lref.areaPrev = area.lightRefs
            area.lightRefs.areaNext = lref
        }

        fun RecurseProcBSP_r(
            results: modelTrace_s?,
            parentNodeNum: Int,
            nodeNum: Int,
            p1f: Float,
            p2f: Float,
            p1: idVec3?,
            p2: idVec3?
        ) {
            val t1: Float
            val t2: Float
            val frac: Float
            val mid = idVec3()
            val side: Int
            val midf: Float
            val node: areaNode_t?
            if (results.fraction <= p1f) {
                return  // already hit something nearer
            }
            // empty leaf
            if (nodeNum < 0) {
                return
            }
            // if solid leaf node
            if (nodeNum == 0) {
                if (parentNodeNum != -1) {
                    results.fraction = p1f
                    results.point.oSet(p1)
                    node = areaNodes.get(parentNodeNum)
                    results.normal.oSet(node.plane.Normal())
                    return
                }
            }
            node = areaNodes.get(nodeNum)

            // distance from plane for trace start and end
            t1 = node.plane.Normal().times(p1) + node.plane.oGet(3)
            t2 = node.plane.Normal().times(p2) + node.plane.oGet(3)
            if (t1 >= 0.0f && t2 >= 0.0f) {
                RecurseProcBSP_r(results, nodeNum, node.children.get(0), p1f, p2f, p1, p2)
                return
            }
            if (t1 < 0.0f && t2 < 0.0f) {
                RecurseProcBSP_r(results, nodeNum, node.children.get(1), p1f, p2f, p1, p2)
                return
            }
            side = if (t1 < t2) 1 else 0
            frac = t1 / (t1 - t2)
            midf = p1f + frac * (p2f - p1f)
            mid.oSet(0, p1.oGet(0) + frac * (p2.oGet(0) - p1.oGet(0)))
            mid.oSet(1, p1.oGet(1) + frac * (p2.oGet(1) - p1.oGet(1)))
            mid.oSet(2, p1.oGet(2) + frac * (p2.oGet(2) - p1.oGet(2)))
            RecurseProcBSP_r(results, nodeNum, node.children.get(side), p1f, midf, p1, mid)
            RecurseProcBSP_r(results, nodeNum, node.children.get(side xor 1), midf, p2f, mid, p2)
        }

        fun BoundsInAreas_r(nodeNum: Int, bounds: idBounds?, areas: IntArray?, numAreas: IntArray?, maxAreas: Int) {
            var nodeNum = nodeNum
            var side: Int
            var i: Int
            var node: areaNode_t?
            do {
                if (nodeNum < 0) {
                    nodeNum = -1 - nodeNum
                    i = 0
                    while (i < numAreas.get(0)) {
                        if (areas.get(i) == nodeNum) {
                            break
                        }
                        i++
                    }
                    if (i >= numAreas.get(0) && numAreas.get(0) < maxAreas) {
                        areas.get(numAreas.get(0)++) = nodeNum
                    }
                    return
                }
                node = areaNodes.get(nodeNum)
                side = bounds.PlaneSide(node.plane)
                nodeNum = if (side == Plane.PLANESIDE_FRONT) {
                    node.children.get(0)
                } else if (side == Plane.PLANESIDE_BACK) {
                    node.children.get(1)
                } else {
                    if (node.children.get(1) != 0) {
                        BoundsInAreas_r(node.children.get(1), bounds, areas, numAreas, maxAreas)
                        if (numAreas.get(0) >= maxAreas) {
                            return
                        }
                    }
                    node.children.get(0)
                }
            } while (nodeNum != 0)
            return
        }

        fun DrawTextLength(text: String?, scale: Float, len: Int /*= 0*/): Float {
            return tr_rendertools.RB_DrawTextLength(text, scale, len)
        }

        fun FreeInteractions() {
            var i: Int
            var def: idRenderEntityLocal?
            i = 0
            while (i < entityDefs.Num()) {
                def = entityDefs.oGet(i)
                if (TempDump.NOT(def)) {
                    i++
                    continue
                }
                // free all the interactions
                while (def.firstInteraction != null) {
                    def.firstInteraction.UnlinkAndFree()
                }
                i++
            }
        }

        /*
         ==================
         PushVolumeIntoTree

         Used for both light volumes and model volumes.

         This does not clip the points by the planes, so some slop
         occurs.

         tr.viewCount should be bumped before calling, allowing it
         to prevent double checking areas.

         We might alternatively choose to do this with an area flow.
         ==================
         */
        fun PushVolumeIntoTree_r(
            def: idRenderEntityLocal?,
            light: idRenderLightLocal?,
            sphere: idSphere?,
            numPoints: Int,
            points: Array<idVec3?>?,
            nodeNum: Int
        ) {
            var nodeNum = nodeNum
            var i: Int
            val node: areaNode_t?
            var front: Boolean
            var back: Boolean
            if (nodeNum < 0) {
                val area: portalArea_s?
                val areaNum = -1 - nodeNum
                area = portalAreas.get(areaNum)
                if (area.viewCount == tr_local.tr.viewCount) {
                    return  // already added a reference here
                }
                area.viewCount = tr_local.tr.viewCount
                def?.let { AddEntityRefToArea(it, area) }
                light?.let { AddLightRefToArea(it, area) }
                return
            }
            node = areaNodes.get(nodeNum)

            // if we know that all possible children nodes only touch an area
            // we have already marked, we can early out
            if (RenderSystem_init.r_useNodeCommonChildren.GetBool()
                && node.commonChildrenArea != RenderWorld_local.CHILDREN_HAVE_MULTIPLE_AREAS
            ) {
                // note that we do NOT try to set a reference in this area
                // yet, because the test volume may yet wind up being in the
                // solid part, which would cause bounds slightly poked into
                // a wall to show up in the next room
                if (portalAreas.get(node.commonChildrenArea).viewCount == tr_local.tr.viewCount) {
                    return
                }
            }

            // if the bounding sphere is completely on one side, don't
            // bother checking the individual points
            val sd = node.plane.Distance(sphere.GetOrigin())
            if (sd >= sphere.GetRadius()) {
                nodeNum = node.children.get(0)
                if (nodeNum != 0) {    // 0 = solid
                    PushVolumeIntoTree_r(def, light, sphere, numPoints, points, nodeNum)
                }
                return
            }
            if (sd <= -sphere.GetRadius()) {
                nodeNum = node.children.get(1)
                if (nodeNum != 0) {    // 0 = solid
                    PushVolumeIntoTree_r(def, light, sphere, numPoints, points, nodeNum)
                }
                return
            }

            // exact check all the points against the node plane
            back = false
            front = back
            //if(MACOS_X){	//loop unrolling & pre-fetching for performance
//	const idVec3 norm = node.plane.Normal();
//	const float plane3 = node.plane[3];
//	float D0, D1, D2, D3;
//
//	for ( i = 0 ; i < numPoints - 4; i+=4 ) {
//		D0 = points[i+0] * norm + plane3;
//		D1 = points[i+1] * norm + plane3;
//		if ( !front && D0 >= 0.0f ) {
//		    front = true;
//		} else if ( !back && D0 <= 0.0f ) {
//		    back = true;
//		}
//		D2 = points[i+1] * norm + plane3;
//		if ( !front && D1 >= 0.0f ) {
//		    front = true;
//		} else if ( !back && D1 <= 0.0f ) {
//		    back = true;
//		}
//		D3 = points[i+1] * norm + plane3;
//		if ( !front && D2 >= 0.0f ) {
//		    front = true;
//		} else if ( !back && D2 <= 0.0f ) {
//		    back = true;
//		}
//		
//		if ( !front && D3 >= 0.0f ) {
//		    front = true;
//		} else if ( !back && D3 <= 0.0f ) {
//		    back = true;
//		}
//		if ( back && front ) {
//		    break;
//		}
//	}
//	if(!(back && front)) {
//		for (; i < numPoints ; i++ ) {
//			float d;
//			d = points[i] * node.plane.Normal() + node.plane[3];
//			if ( d >= 0.0f ) {
//				front = true;
//			} else if ( d <= 0.0f ) {
//				back = true;
//			}
//			if ( back && front ) {
//				break;
//			}
//		}	
//	}
//}else
            run {
                i = 0
                while (i < numPoints) {
                    var d: Float
                    d = points.get(i).times(node.plane.Normal()) + node.plane.oGet(3)
                    if (d >= 0.0f) {
                        front = true
                    } else if (d <= 0.0f) {
                        back = true
                    }
                    if (back && front) {
                        break
                    }
                    i++
                }
            }
            if (front) {
                nodeNum = node.children.get(0)
                if (nodeNum != 0) {    // 0 = solid
                    PushVolumeIntoTree_r(def, light, sphere, numPoints, points, nodeNum)
                }
            }
            if (back) {
                nodeNum = node.children.get(1)
                if (nodeNum != 0) {    // 0 = solid
                    PushVolumeIntoTree_r(def, light, sphere, numPoints, points, nodeNum)
                }
            }
        }

        fun PushVolumeIntoTree(
            def: idRenderEntityLocal?,
            light: idRenderLightLocal?,
            numPoints: Int,
            points: Array<idVec3?>?
        ) {
            var i: Int
            var radSquared: Float
            var lr: Float
            val mid = idVec3()
            val dir = idVec3()
            if (areaNodes == null) {
                return
            }

            // calculate a bounding sphere for the points
            mid.Zero()
            i = 0
            while (i < numPoints) {
                mid.plusAssign(points.get(i))
                i++
            }
            mid.timesAssign(1.0f / numPoints)
            radSquared = 0f
            i = 0
            while (i < numPoints) {
                dir.oSet(points.get(i).oMinus(mid))
                lr = dir.times(dir)
                if (lr > radSquared) {
                    radSquared = lr
                }
                i++
            }
            val sphere = idSphere(mid, Math.sqrt(radSquared.toDouble()).toFloat())
            PushVolumeIntoTree_r(def, light, sphere, numPoints, points, 0)
        }

        //===============================================================================================================
        // tr_light.c
        /*
         =================
         idRenderWorldLocal::CreateLightDefInteractions

         When a lightDef is determined to effect the view (contact the frustum and non-0 light), it will check to
         make sure that it has interactions for all the entityDefs that it might possibly contact.

         This does not guarantee that all possible interactions for this light are generated, only that
         the ones that may effect the current view are generated. so it does need to be called every view.

         This does not cause entityDefs to create dynamic models, all work is done on the referenceBounds.

         All entities that have non-empty interactions with viewLights will
         have viewEntities made for them and be put on the viewEntity list,
         even if their surfaces aren't visible, because they may need to cast shadows.

         Interactions are usually removed when a entityDef or lightDef is modified, unless the change
         is known to not effect them, so there is no danger of getting a stale interaction, we just need to
         check that needed ones are created.

         An interaction can be at several levels:

         Don't interact (but share an area) (numSurfaces = 0)
         Entity reference bounds touches light frustum, but surfaces haven't been generated (numSurfaces = -1)
         Shadow surfaces have been generated, but light surfaces have not.  The shadow surface may still be empty due to bounds being conservative.
         Both shadow and light surfaces have been generated.  Either or both surfaces may still be empty due to conservative bounds.

         =================
         */
        fun CreateLightDefInteractions(lDef: idRenderLightLocal?) {
            var eRef: areaReference_s?
            var lRef: areaReference_s?
            var eDef: idRenderEntityLocal?
            var area: portalArea_s?
            var inter: idInteraction?
            var i = 0
            var j = 0
            lRef = lDef.references
            while (lRef != null) {
                area = lRef.area

                // check all the models in this area
                eRef = area.entityRefs.areaNext
                while (eRef !== area.entityRefs) {
                    eDef = eRef.entity

                    // if the entity doesn't have any light-interacting surfaces, we could skip this,
                    // but we don't want to instantiate dynamic models yet, so we can't check that on
                    // most things
                    // if the entity isn't viewed
                    if (tr_local.tr.viewDef != null && eDef.viewCount != tr_local.tr.viewCount) {
                        // if the light doesn't cast shadows, skip
                        if (!lDef.lightShader.LightCastsShadows()) {
                            eRef = eRef.areaNext
                            j++
                            continue
                        }
                        // if we are suppressing its shadow in this view, skip
                        if (!RenderSystem_init.r_skipSuppress.GetBool()) {
                            if (eDef.parms.suppressShadowInViewID != 0 && eDef.parms.suppressShadowInViewID == tr_local.tr.viewDef.renderView.viewID) {
                                eRef = eRef.areaNext
                                j++
                                continue
                            }
                            if (eDef.parms.suppressShadowInLightID != 0 && eDef.parms.suppressShadowInLightID == lDef.parms.lightId) {
                                eRef = eRef.areaNext
                                j++
                                continue
                            }
                        }
                    }

                    // some big outdoor meshes are flagged to not create any dynamic interactions
                    // when the level designer knows that nearby moving lights shouldn't actually hit them
                    if (eDef.parms.noDynamicInteractions && eDef.world.generateAllInteractionsCalled) {
                        eRef = eRef.areaNext
                        j++
                        continue
                    }

                    // if any of the edef's interaction match this light, we don't
                    // need to consider it. 
                    if (RenderSystem_init.r_useInteractionTable.GetBool() && interactionTable != null) {
                        // allocating these tables may take several megs on big maps, but it saves 3% to 5% of
                        // the CPU time.  The table is updated at interaction::AllocAndLink() and interaction::UnlinkAndFree()
                        val index = lDef.index * interactionTableWidth + eDef.index
                        inter = interactionTable.get(index)
                        if (index == 441291) {
                            val x = 0
                        }
                        if (inter != null) {
                            // if this entity wasn't in view already, the scissor rect will be empty,
                            // so it will only be used for shadow casting
                            if (!inter.IsEmpty()) {
                                tr_light.R_SetEntityDefViewEntity(eDef)
                            }
                            eRef = eRef.areaNext
                            j++
                            continue
                        }
                    } else {
                        // scan the doubly linked lists, which may have several dozen entries

                        // we could check either model refs or light refs for matches, but it is
                        // assumed that there will be less lights in an area than models
                        // so the entity chains should be somewhat shorter (they tend to be fairly close).
                        inter = eDef.firstInteraction
                        while (inter != null) {
                            if (inter.lightDef === lDef) {
                                break
                            }
                            inter = inter.entityNext
                        }

                        // if we already have an interaction, we don't need to do anything
                        if (inter != null) {
                            // if this entity wasn't in view already, the scissor rect will be empty,
                            // so it will only be used for shadow casting
                            if (!inter.IsEmpty()) {
                                tr_light.R_SetEntityDefViewEntity(eDef)
                            }
                            eRef = eRef.areaNext
                            j++
                            continue
                        }
                    }

                    //
                    // create a new interaction, but don't do any work other than bbox to frustum culling
                    //
                    inter = idInteraction.Companion.AllocAndLink(eDef, lDef)

                    // do a check of the entity reference bounds against the light frustum,
                    // trying to avoid creating a viewEntity if it hasn't been already
                    val modelMatrix = FloatArray(16)
                    var m: FloatArray?
                    m = if (eDef.viewCount == tr_local.tr.viewCount) {
                        eDef.viewEntity.modelMatrix
                    } else {
                        tr_main.R_AxisToModelMatrix(eDef.parms.axis, eDef.parms.origin, modelMatrix)
                        modelMatrix
                    }
                    if (tr_main.R_CullLocalBox(eDef.referenceBounds, m, 6, lDef.frustum)) {
                        inter.MakeEmpty()
                        eRef = eRef.areaNext
                        j++
                        continue
                    }

                    // we will do a more precise per-surface check when we are checking the entity
                    // if this entity wasn't in view already, the scissor rect will be empty,
                    // so it will only be used for shadow casting
                    tr_light.R_SetEntityDefViewEntity(eDef)
                    eRef = eRef.areaNext
                    j++
                }
                lRef = lRef.ownerNext
                i++
            }
        }

        companion object {
            val playerMaterialExcludeList: Array<String?>? = arrayOf(
                "muzzlesmokepuff",
                null
            )

            // FIXME: _D3XP added those.
            val playerModelExcludeList: Array<String?>? = arrayOf(
                "models/md5/characters/player/d3xp_spplayer.md5mesh",
                "models/md5/characters/player/head/d3xp_head.md5mesh",
                "models/md5/weapons/pistol_world/worldpistol.md5mesh",
                null
            )
            private val arrowCos: FloatArray? = FloatArray(40)
            private val arrowSin: FloatArray? = FloatArray(40)

            /*
         ===================
         AddAreaLightRefs

         This is the only point where lights get added to the viewLights list
         ===================
         */
            var DEBUG_AddAreaLightRefs = 0
            private var arrowStep = 0

            /*
         =============
         FindViewLightsAndEntites

         All the modelrefs and lightrefs that are in visible areas
         will have viewEntitys and viewLights created for them.

         The scissorRects on the viewEntitys and viewLights may be empty if
         they were considered, but not actually visible.
         =============
         */
            private var lastPrintedAreaNum = 0
        }

        init {
            mapName = idStr() //.Clear();
            mapTimeStamp = longArrayOf(FileSystem_h.FILE_NOT_FOUND_TIMESTAMP.toLong())
            generateAllInteractionsCalled = false
            areaNodes = null
            numAreaNodes = 0
            portalAreas = null
            numPortalAreas = 0
            doublePortals = null
            numInterAreaPortals = 0
            interactionTable = null
            interactionTableWidth = 0
            interactionTableHeight = 0
        }
    }
}