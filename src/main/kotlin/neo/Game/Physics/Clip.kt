package neo.Game.Physics

import neo.CM.CollisionModel
import neo.CM.CollisionModel.contactInfo_t
import neo.CM.CollisionModel.contactType_t
import neo.CM.CollisionModel.trace_s
import neo.CM.CollisionModel_local
import neo.Game.Entity.idEntity
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local
import neo.Game.Game_local.idGameLocal
import neo.Renderer.*
import neo.Renderer.RenderWorld.modelTrace_s
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CBool
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.idList
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.math.*
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Rotation.idRotation
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec6
import java.util.*

/**
 *
 */
object Clip {
    const val MAX_SECTOR_DEPTH = 12
    const val MAX_SECTORS = (1 shl Clip.MAX_SECTOR_DEPTH + 1) - 1
    val vec3_boxEpsilon: idVec3? =
        idVec3(CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON, CollisionModel.CM_BOX_EPSILON)

    //    public static final idBlockAlloc<clipLink_s> clipLinkAllocator = new idBlockAlloc<>(1024);
    /*
     ===============================================================

     idClipModel trace model cache

     ===============================================================
     */
    val traceModelCache: idList<trmCache_s?>? = idList()
    val traceModelHash: idHashIndex? = idHashIndex()

    /*
     ===============================================================================

     Handles collision detection with the world and between physics objects.

     ===============================================================================
     */
    fun CLIPMODEL_ID_TO_JOINT_HANDLE(id: Int): Int {
        return if (id >= 0) Model.INVALID_JOINT else -1 - id
    }

    fun JOINT_HANDLE_TO_CLIPMODEL_ID(id: Int): Int {
        return -1 - id
    }

    /*
     ============
     idClip::TestHugeTranslation
     ============
     */
    fun TestHugeTranslation(
        results: trace_s?,
        mdl: idClipModel?,
        start: idVec3?,
        end: idVec3?,
        trmAxis: idMat3?
    ): Boolean {
        if (mdl != null && end.oMinus(start).LengthSqr() > Math_h.Square(CollisionModel.CM_MAX_TRACE_DIST)) {
            // assert (false);
            results.fraction = 0.0f
            results.endpos.oSet(start)
            results.endAxis.oSet(trmAxis)
            results.c = contactInfo_t() //memset( results.c, 0, sizeof( results.c ) );
            results.c.point.oSet(start)
            results.c.entityNum = Game_local.ENTITYNUM_WORLD
            if (mdl.GetEntity() != null) {
                Game_local.gameLocal.Printf(
                    "huge translation for clip model %d on entity %d '%s'\n",
                    mdl.GetId(),
                    mdl.GetEntity().entityNumber,
                    mdl.GetEntity().GetName()
                )
            } else {
                Game_local.gameLocal.Printf("huge translation for clip model %d\n", mdl.GetId())
            }
            return true
        }
        return false
    }

    class clipSector_s {
        var axis // -1 = leaf node
                = 0
        var children: Array<clipSector_s?>? = arrayOfNulls<clipSector_s?>(2)
        var clipLinks: clipLink_s? = null
        var dist = 0f //        private void oSet(clipSector_s clip) {
        //            this.axis = clip.axis;
        //            this.dist = clip.dist;
        //            this.children = clip.children;
        //            this.clipLinks = clip.clipLinks;
        //        }
    }

    class clipLink_s {
        var clipModel: idClipModel? = null
        var nextInSector: clipLink_s? = null
        var nextLink: clipLink_s? = null
        var prevInSector: clipLink_s? = null
        var sector: clipSector_s? = null
    }

    class trmCache_s {
        val centerOfMass: idVec3?
        var inertiaTensor: idMat3?
        var refCount = 0
        var trm: idTraceModel? = null
        var volume = 0f

        init {
            centerOfMass = idVec3()
            inertiaTensor = idMat3()
        }
    }

    class idClipModel {
        private val absBounds: idBounds? = idBounds() // absolute bounds
        private val axis: idMat3? = idMat3() // orientation of clip model
        private val bounds: idBounds? = idBounds() // bounds
        private val origin: idVec3? = idVec3() // origin of clip model

        //
        private var clipLinks // links into sectors
                : clipLink_s? = null
        private var   /*cmHandle_t*/collisionModelHandle // handle to collision model
                = 0
        private var contents // all contents ored together
                = 0
        private var enabled // true if this clip model is used for clipping
                = false
        private var entity // entity using this clip model
                : idEntity? = null
        private var id // id for entities that use multiple clip models
                = 0
        private var material // material for trace models
                : idMaterial? = null
        private var owner // owner of the entity that owns this clip model
                : idEntity? = null
        private var renderModelHandle // render model def handle
                = 0
        private var touchCount = 0
        private var traceModelIndex // trace model used for collision detection
                = 0

        // friend class idClip;
        constructor() {
            Init()
        }

        constructor(name: String?) {
            Init()
            LoadModel(name)
        }

        constructor(trm: idTraceModel?) {
            Init()
            LoadModel(trm)
        }

        // ~idClipModel( void );
        constructor(renderModelHandle: Int) {
            Init()
            contents = Material.CONTENTS_RENDERMODEL
            LoadModel(renderModelHandle)
        }

        constructor(model: idClipModel?) {
            enabled = model.enabled
            entity = model.entity
            id = model.id
            owner = model.owner
            origin.oSet(model.origin)
            axis.oSet(model.axis)
            bounds.oSet(model.bounds)
            absBounds.oSet(model.absBounds)
            material = model.material
            contents = model.contents
            collisionModelHandle = model.collisionModelHandle
            traceModelIndex = -1
            if (model.traceModelIndex != -1) {
                LoadModel(GetCachedTraceModel(model.traceModelIndex))
            }
            renderModelHandle = model.renderModelHandle
            clipLinks = null
            touchCount = -1
        }

        fun LoadModel(name: String?): Boolean {
            renderModelHandle = -1
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
                traceModelIndex = -1
            }
            collisionModelHandle = CollisionModel_local.collisionModelManager.LoadModel(name, false)
            return if (collisionModelHandle != 0) {
                CollisionModel_local.collisionModelManager.GetModelBounds(collisionModelHandle, bounds)
                run {
                    val contents = CInt()
                    CollisionModel_local.collisionModelManager.GetModelContents(collisionModelHandle, contents)
                    this.contents = contents.getVal()
                }
                true
            } else {
                bounds.Zero()
                false
            }
        }

        fun LoadModel(trm: idTraceModel?) {
            collisionModelHandle = 0
            renderModelHandle = -1
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
            }
            traceModelIndex = AllocTraceModel(trm)
            bounds.oSet(trm.bounds)
        }

        fun LoadModel(renderModelHandle: Int) {
            collisionModelHandle = 0
            this.renderModelHandle = renderModelHandle
            if (renderModelHandle != -1) {
                val renderEntity = Game_local.gameRenderWorld.GetRenderEntity(renderModelHandle)
                if (renderEntity != null) {
                    bounds.oSet(renderEntity.bounds)
                }
            }
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
                traceModelIndex = -1
            }
        }

        fun Save(savefile: idSaveGame?) {
            savefile.WriteBool(enabled)
            savefile.WriteObject(entity)
            savefile.WriteInt(id)
            savefile.WriteObject(owner)
            savefile.WriteVec3(origin)
            savefile.WriteMat3(axis)
            savefile.WriteBounds(bounds)
            savefile.WriteBounds(absBounds)
            savefile.WriteMaterial(material)
            savefile.WriteInt(contents)
            if (collisionModelHandle >= 0) {
                savefile.WriteString(CollisionModel_local.collisionModelManager.GetModelName(collisionModelHandle))
            } else {
                savefile.WriteString("")
            }
            savefile.WriteInt(traceModelIndex)
            savefile.WriteInt(renderModelHandle)
            savefile.WriteBool(clipLinks != null)
            savefile.WriteInt(touchCount)
        }

        fun Restore(savefile: idRestoreGame?) {
            val collisionModelName = idStr()
            val linked = CBool(false)
            enabled = savefile.ReadBool()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/entity)
            id = savefile.ReadInt()
            savefile.ReadObject( /*reinterpret_cast<idClass *&>*/owner)
            savefile.ReadVec3(origin)
            savefile.ReadMat3(axis)
            savefile.ReadBounds(bounds)
            savefile.ReadBounds(absBounds)
            savefile.ReadMaterial(material)
            contents = savefile.ReadInt()
            savefile.ReadString(collisionModelName)
            collisionModelHandle = if (collisionModelName.Length() != 0) {
                CollisionModel_local.collisionModelManager.LoadModel(collisionModelName.toString(), false)
            } else {
                -1
            }
            traceModelIndex = savefile.ReadInt()
            if (traceModelIndex >= 0) {
                Clip.traceModelCache.oGet(traceModelIndex).refCount++
            }
            renderModelHandle = savefile.ReadInt()
            savefile.ReadBool(linked)
            touchCount = savefile.ReadInt()

            // the render model will be set when the clip model is linked
            renderModelHandle = -1
            clipLinks = null
            touchCount = -1
            if (linked.isVal) {
                Link(Game_local.gameLocal.clip, entity, id, origin, axis, renderModelHandle)
            }
        }

        fun Link(clp: idClip?) {                // must have been linked with an entity and id before
            assert(entity != null)
            if (null == entity) {
                return
            }
            if (clipLinks != null) {
                Unlink() // unlink from old position
            }
            if (bounds.IsCleared()) {
                return
            }

            // set the abs box
            if (axis.IsRotated()) {
                // expand for rotation
                absBounds.FromTransformedBounds(bounds, origin, axis)
            } else {
                // normal
                absBounds.oSet(0, bounds.oGet(0).oPlus(origin))
                absBounds.oSet(1, bounds.oGet(1).oPlus(origin))
            }

            // because movement is clipped an epsilon away from an actual edge,
            // we must fully check even when bounding boxes don't quite touch
            absBounds.oMinSet(0, Clip.vec3_boxEpsilon)
            absBounds.timesAssign(1, Clip.vec3_boxEpsilon)
            Link_r(clp.clipSectors.get(0)) //TODO:check if [0] is good enough. upd: seems it is
        }

        @JvmOverloads
        fun Link(
            clp: idClip?,
            ent: idEntity?,
            newId: Int,
            newOrigin: idVec3?,
            newAxis: idMat3?,
            renderModelHandle: Int = -1 /*= -1*/
        ) {
            entity = ent
            id = newId
            origin.oSet(newOrigin)
            axis.oSet(newAxis)
            if (renderModelHandle != -1) {
                this.renderModelHandle = renderModelHandle
                val renderEntity = Game_local.gameRenderWorld.GetRenderEntity(renderModelHandle)
                if (renderEntity != null) {
                    bounds.oSet(renderEntity.bounds)
                }
            }
            this.Link(clp)
        }

        fun Unlink() {                        // unlink from sectors
            var link: clipLink_s?
            link = clipLinks
            while (link != null) {
                clipLinks = link.nextLink
                if (link.prevInSector != null) {
                    link.prevInSector.nextInSector = link.nextInSector
                } else {
                    link.sector.clipLinks = link.nextInSector
                }
                if (link.nextInSector != null) {
                    link.nextInSector.prevInSector = link.prevInSector
                }
                link = clipLinks
            }
        }

        fun SetPosition(newOrigin: idVec3?, newAxis: idMat3?) {    // unlinks the clip model
            if (clipLinks != null) {
                Unlink() // unlink from old position
            }
            origin.oSet(newOrigin)
            axis.oSet(newAxis)
        }

        fun Translate(translation: idVec3?) {                            // unlinks the clip model
            Unlink()
            origin.plusAssign(translation)
        }

        fun Rotate(rotation: idRotation?) {                            // unlinks the clip model
            Unlink()
            origin.timesAssign(rotation)
            axis.timesAssign(rotation.ToMat3())
        }

        fun Enable() {                        // enable for clipping
            enabled = true
        }

        fun Disable() {                    // keep linked but disable for clipping
            enabled = false
        }

        fun SetMaterial(m: idMaterial?) {
            material = m
        }

        fun GetMaterial(): idMaterial? {
            return material
        }

        fun SetContents(newContents: Int) {        // override contents
            contents = newContents
        }

        fun GetContents(): Int {
            return contents
        }

        fun SetEntity(newEntity: idEntity?) {
            entity = newEntity
        }

        fun GetEntity(): idEntity? {
            return entity
        }

        fun SetId(newId: Int) {
            id = newId
        }

        fun GetId(): Int {
            return id
        }

        fun SetOwner(newOwner: idEntity?) {
            owner = newOwner
        }

        fun GetOwner(): idEntity? {
            return owner
        }

        fun GetBounds(): idBounds? {
            return idBounds(bounds)
        }

        fun GetAbsBounds(): idBounds? {
            return idBounds(absBounds)
        }

        fun GetOrigin(): idVec3? {
            return origin
        }

        fun GetAxis(): idMat3? {
            return axis
        }

        fun IsTraceModel(): Boolean {            // returns true if this is a trace model
            return traceModelIndex != -1
        }

        fun IsRenderModel(): Boolean {        // returns true if this is a render model
            return renderModelHandle != -1
        }

        fun IsLinked(): Boolean {                // returns true if the clip model is linked
            return clipLinks != null
        }

        fun IsEnabled(): Boolean {            // returns true if enabled for collision detection
            return enabled
        }

        fun IsEqual(trm: idTraceModel?): Boolean {
            return traceModelIndex != -1 && GetCachedTraceModel(traceModelIndex) === trm
        }

        fun  /*cmHandle_t*/Handle(): Int {                // returns handle used to collide vs this model
            assert(renderModelHandle == -1)
            return if (collisionModelHandle != 0) {
                collisionModelHandle
            } else if (traceModelIndex != -1) {
                CollisionModel_local.collisionModelManager.SetupTrmModel(GetCachedTraceModel(traceModelIndex), material)
            } else {
                // this happens in multiplayer on the combat models
                Game_local.gameLocal.Warning(
                    "idClipModel::Handle: clip model %d on '%s' (%x) is not a collision or trace model",
                    id,
                    entity.name,
                    entity.entityNumber
                )
                0
            }
        }

        fun GetTraceModel(): idTraceModel? {
            return if (!IsTraceModel()) {
                null
            } else GetCachedTraceModel(traceModelIndex)
        }

        fun GetMassProperties(density: Float, mass: CFloat?, centerOfMass: idVec3?, inertiaTensor: idMat3?) {
            if (traceModelIndex == -1) {
                idGameLocal.Companion.Error(
                    "idClipModel::GetMassProperties: clip model %d on '%s' is not a trace model\n",
                    id,
                    entity.name
                )
            }
            val entry: trmCache_s? = Clip.traceModelCache.oGet(traceModelIndex)
            mass.setVal(Math.abs(entry.volume * density)) // a hack-fix
            centerOfMass.oSet(entry.centerOfMass)
            inertiaTensor.oSet(entry.inertiaTensor.times(density))
        }

        // initialize(or does it?)
        private fun Init() {
            enabled = true
            entity = null
            id = 0
            owner = null
            origin.Zero()
            axis.Identity()
            bounds.Zero()
            absBounds.Zero()
            material = null
            contents = Material.CONTENTS_BODY
            collisionModelHandle = 0
            renderModelHandle = -1
            traceModelIndex = -1
            clipLinks = null
            touchCount = -1
        }

        private fun Link_r(node: clipSector_s?) {
            var node = node
            val link: clipLink_s
            while (node.axis != -1) {
                node = if (absBounds.oGet(0, node.axis) > node.dist) {
                    node.children.get(0)
                } else if (absBounds.oGet(1, node.axis) < node.dist) {
                    node.children.get(1)
                } else {
                    Link_r(node.children.get(0))
                    node.children.get(1)
                }
            }
            link = clipLink_s() //clipLinkAllocator.Alloc();
            link.clipModel = this
            link.sector = node
            link.nextInSector = node.clipLinks
            link.prevInSector = null
            if (node.clipLinks != null) {
                node.clipLinks.prevInSector = link
            }
            node.clipLinks = link
            link.nextLink = clipLinks
            clipLinks = link
        }

        fun oSet(idClipModel: idClipModel?) {
            throw UnsupportedOperationException("Not supported yet.") //To change body of generated methods, choose Tools | Templates.
        }

        protected fun _deconstructor() {
            // make sure the clip model is no longer linked
            Unlink()
            if (traceModelIndex != -1) {
                FreeTraceModel(traceModelIndex)
            }
        }

        companion object {
            fun  /*cmHandle_t*/CheckModel(name: String?): Int {
                return CollisionModel_local.collisionModelManager.LoadModel(name, false)
            }

            fun  /*cmHandle_t*/CheckModel(name: idStr?): Int {
                return CheckModel(name.toString())
            }

            fun ClearTraceModelCache() {
                Clip.traceModelCache.DeleteContents(true)
                Clip.traceModelHash.Free()
            }

            fun SaveTraceModels(savefile: idSaveGame?) {
                var i: Int
                savefile.WriteInt(Clip.traceModelCache.Num())
                i = 0
                while (i < Clip.traceModelCache.Num()) {
                    val entry: trmCache_s? = Clip.traceModelCache.oGet(i)
                    savefile.WriteTraceModel(entry.trm)
                    savefile.WriteFloat(entry.volume)
                    savefile.WriteVec3(entry.centerOfMass)
                    savefile.WriteMat3(entry.inertiaTensor)
                    i++
                }
            }

            fun RestoreTraceModels(savefile: idRestoreGame?) {
                var i: Int
                val num = CInt()
                ClearTraceModelCache()
                savefile.ReadInt(num)
                Clip.traceModelCache.SetNum(num.getVal())
                i = 0
                while (i < num.getVal()) {
                    val entry = trmCache_s()
                    savefile.ReadTraceModel(entry.trm)
                    entry.volume = savefile.ReadFloat()
                    savefile.ReadVec3(entry.centerOfMass)
                    savefile.ReadMat3(entry.inertiaTensor)
                    entry.refCount = 0
                    Clip.traceModelCache.oSet(i, entry)
                    Clip.traceModelHash.Add(GetTraceModelHashKey(entry.trm), i)
                    i++
                }
            }

            private fun AllocTraceModel(trm: idTraceModel?): Int {
                var i: Int
                val hashKey: Int
                val traceModelIndex: Int
                val entry: trmCache_s
                hashKey = GetTraceModelHashKey(trm)
                i = Clip.traceModelHash.First(hashKey)
                while (i >= 0) {
                    if (Clip.traceModelCache.oGet(i).trm == trm) {
                        Clip.traceModelCache.oGet(i).refCount++
                        return i
                    }
                    i = Clip.traceModelHash.Next(i)
                }
                entry = trmCache_s()
                entry.trm = trm
                val volume = CFloat()
                entry.trm.GetMassProperties(1.0f, volume, entry.centerOfMass, entry.inertiaTensor)
                entry.volume = volume.getVal()
                entry.refCount = 1
                traceModelIndex = Clip.traceModelCache.Append(entry)
                Clip.traceModelHash.Add(hashKey, traceModelIndex)
                return traceModelIndex
            }

            private fun FreeTraceModel(traceModelIndex: Int) {
                if (traceModelIndex < 0 || traceModelIndex >= Clip.traceModelCache.Num() || Clip.traceModelCache.oGet(
                        traceModelIndex
                    ).refCount <= 0
                ) {
                    Game_local.gameLocal.Warning("idClipModel::FreeTraceModel: tried to free uncached trace model")
                    return
                }
                Clip.traceModelCache.oGet(traceModelIndex).refCount--
            }

            private fun GetCachedTraceModel(traceModelIndex: Int): idTraceModel? {
                return Clip.traceModelCache.oGet(traceModelIndex).trm
            }

            private fun GetTraceModelHashKey(trm: idTraceModel?): Int {
                val v = trm.bounds.oGet(0)
                return trm.type.ordinal shl 8 xor (trm.numVerts shl 4) xor (trm.numEdges shl 2) xor (trm.numPolys shl 0) xor idMath.FloatHash(
                    v.ToFloatPtr(),
                    v.GetDimension()
                )
            }

            fun delete(clipModel: idClipModel?) {
                clipModel._deconstructor()
            }
        }
    }

    //===============================================================
    //
    //	idClip
    //
    //===============================================================
    class idClip {
        // friend class idClipModel;
        private val defaultClipModel: idClipModel? = idClipModel()
        private val temporaryClipModel: idClipModel? = idClipModel()
        private val worldBounds: idBounds?
        private var clipSectors: Array<clipSector_s?>? = null
        private var numClipSectors = 0
        private var numContacts: Int
        private var numContents: Int
        private var numMotions: Int
        private var numRenderModelTraces: Int
        private var numRotations: Int

        // statistics
        private var numTranslations: Int
        private var touchCount = 0
        fun Init() {
            val   /*cmHandle_t*/h: Int
            val size = idVec3()
            val maxSector = Vector.getVec3_origin()

            // clear clip sectors
            clipSectors = arrayOfNulls<clipSector_s?>(Clip.MAX_SECTORS)
            //	memset( clipSectors, 0, MAX_SECTORS * sizeof( clipSector_t ) );
            numClipSectors = 0
            touchCount = -1
            // get world map bounds
            h = CollisionModel_local.collisionModelManager.LoadModel("worldMap", false)
            CollisionModel_local.collisionModelManager.GetModelBounds(h, worldBounds)
            // create world sectors
            CreateClipSectors_r(0, worldBounds, maxSector)
            size.oSet(worldBounds.oGet(1).oMinus(worldBounds.oGet(0)))
            Game_local.gameLocal.Printf(
                "map bounds are (%1.1f, %1.1f, %1.1f)\n",
                size.oGet(0),
                size.oGet(1),
                size.oGet(2)
            )
            Game_local.gameLocal.Printf(
                "max clip sector is (%1.1f, %1.1f, %1.1f)\n",
                maxSector.oGet(0),
                maxSector.oGet(1),
                maxSector.oGet(2)
            )

            // initialize a default clip model
            defaultClipModel.LoadModel(idTraceModel(idBounds(idVec3(0, 0, 0)).Expand(8f)))

            // set counters to zero
            numContacts = 0
            numContents = numContacts
            numRenderModelTraces = numContents
            numMotions = numRenderModelTraces
            numTranslations = numMotions
            numRotations = numTranslations
        }

        fun Shutdown() {
//	delete[] clipSectors;
            clipSectors = null

            // free the trace model used for the temporaryClipModel
            if (temporaryClipModel.traceModelIndex != -1) {
                idClipModel.FreeTraceModel(temporaryClipModel.traceModelIndex)
                temporaryClipModel.traceModelIndex = -1
            }

            // free the trace model used for the defaultClipModel
            if (defaultClipModel.traceModelIndex != -1) {
                idClipModel.FreeTraceModel(defaultClipModel.traceModelIndex)
                defaultClipModel.traceModelIndex = -1
            }
            //
//            clipLinkAllocator.Shutdown();
        }

        // clip versus the rest of the world
        fun Translation(
            results: trace_s?, start: idVec3?, end: idVec3?,
            mdl: idClipModel?, trmAxis: idMat3?, contentMask: Int, passEntity: idEntity?
        ): Boolean {
            var i: Int
            val num: Int
            var touch: idClipModel
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val radius: Float
            val trace = trace_s()
            val trm: idTraceModel?
            if (Clip.TestHugeTranslation(results, mdl, start, end, trmAxis)) {
                return true
            }
            trm = TraceModelForClipModel(mdl)
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                numTranslations++
                CollisionModel_local.collisionModelManager.Translation(
                    results,
                    start,
                    end,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    Vector.getVec3_origin(),
                    idMat3.Companion.getMat3_default()
                )
                results.c.entityNum =
                    if (results.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
                if (results.fraction == 0.0f) {
                    return true // blocked immediately by the world
                }
            } else {
                results.fraction = 1.0f
                results.endpos.oSet(end)
                results.endAxis.oSet(trmAxis)
            }
            radius = if (null == trm) {
                traceBounds.FromPointTranslation(start, results.endpos.oMinus(start))
                0.0f
            } else {
                traceBounds.FromBoundsTranslation(trm.bounds, start, trmAxis, results.endpos.oMinus(start))
                trm.bounds.GetRadius()
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }
                if (touch.renderModelHandle != -1) {
                    numRenderModelTraces++
                    TraceRenderModel(trace, start, end, radius, trmAxis, touch)
                } else {
                    numTranslations++
                    CollisionModel_local.collisionModelManager.Translation(
                        trace,
                        start,
                        end,
                        trm,
                        trmAxis,
                        contentMask,
                        touch.Handle(),
                        touch.origin,
                        touch.axis
                    )
                }
                if (trace.fraction < results.fraction) {
                    results.oSet(trace)
                    results.c.entityNum = touch.entity.entityNumber
                    results.c.id = touch.id
                    if (results.fraction == 0.0f) {
                        break
                    }
                }
                i++
            }
            return results.fraction < 1.0f
        }

        fun Rotation(
            results: trace_s?, start: idVec3?, rotation: idRotation?,
            mdl: idClipModel?, trmAxis: idMat3?, contentMask: Int, passEntity: idEntity?
        ): Boolean {
            var i: Int
            val num: Int
            var touch: idClipModel
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val trace = trace_s()
            val trm: idTraceModel?
            trm = TraceModelForClipModel(mdl)
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                numRotations++
                CollisionModel_local.collisionModelManager.Rotation(
                    results,
                    start,
                    rotation,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    Vector.getVec3_origin(),
                    idMat3.Companion.getMat3_default()
                )
                results.c.entityNum =
                    if (results.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
                if (results.fraction == 0.0f) {
                    return true // blocked immediately by the world
                }
            } else {
//		memset( &results, 0, sizeof( results ) );
                results.fraction = 1.0f
                results.endpos.oSet(start)
                results.endAxis.oSet(trmAxis.times(rotation.ToMat3()))
            }
            if (null == trm) {
                traceBounds.FromPointRotation(start, rotation)
            } else {
                traceBounds.FromBoundsRotation(trm.bounds, start, trmAxis, rotation)
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }

                // no rotational collision with render models
                if (touch.renderModelHandle != -1) {
                    i++
                    continue
                }
                numRotations++
                CollisionModel_local.collisionModelManager.Rotation(
                    trace,
                    start,
                    rotation,
                    trm,
                    trmAxis,
                    contentMask,
                    touch.Handle(),
                    touch.origin,
                    touch.axis
                )
                if (trace.fraction < results.fraction) {
                    results.oSet(trace)
                    results.c.entityNum = touch.entity.entityNumber
                    results.c.id = touch.id
                    if (results.fraction == 0.0f) {
                        break
                    }
                }
                i++
            }
            return results.fraction < 1.0f
        }

        fun Motion(
            results: trace_s?, start: idVec3?, end: idVec3?, rotation: idRotation?,
            mdl: idClipModel?, trmAxis: idMat3?, contentMask: Int, passEntity: idEntity?
        ): Boolean {
            var i: Int
            var num: Int
            var touch: idClipModel
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            val dir = idVec3()
            val endPosition = idVec3()
            val traceBounds = idBounds()
            val radius: Float
            var translationalTrace: trace_s? = trace_s()
            var rotationalTrace = trace_s()
            val trace = trace_s()
            val endRotation: idRotation
            val trm: idTraceModel?
            assert(rotation.GetOrigin() == start)
            if (Clip.TestHugeTranslation(results, mdl, start, end, trmAxis)) {
                return true
            }
            if (mdl != null && rotation.GetAngle() != 0.0f && rotation.GetVec() != Vector.getVec3_origin()) {
                // if no translation
                if (start === end) {
                    // pure rotation
                    return Rotation(results, start, rotation, mdl, trmAxis, contentMask, passEntity)
                }
            } else if (start !== end) {
                // pure translation
                return Translation(results, start, end, mdl, trmAxis, contentMask, passEntity)
            } else {
                // no motion
                results.fraction = 1.0f
                results.endpos.oSet(start)
                results.endAxis.oSet(trmAxis)
                return false
            }
            trm = TraceModelForClipModel(mdl)
            radius = trm.bounds.GetRadius()
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // translational collision with world
                numTranslations++
                CollisionModel_local.collisionModelManager.Translation(
                    translationalTrace,
                    start,
                    end,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    Vector.getVec3_origin(),
                    idMat3.Companion.getMat3_default()
                )
                translationalTrace.c.entityNum =
                    if (translationalTrace.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
            } else {
//		memset( &translationalTrace, 0, sizeof( translationalTrace ) );
                translationalTrace = trace_s()
                translationalTrace.fraction = 1.0f
                translationalTrace.endpos.oSet(end)
                translationalTrace.endAxis.oSet(trmAxis)
            }
            if (translationalTrace.fraction != 0.0f) {
                traceBounds.FromBoundsRotation(trm.bounds, start, trmAxis, rotation)
                dir.oSet(translationalTrace.endpos.oMinus(start))
                i = 0
                while (i < 3) {
                    if (dir.oGet(i) < 0.0f) {
                        traceBounds.oGet(0).plusAssign(i, dir.oGet(i))
                    } else {
                        traceBounds.oGet(1).plusAssign(i, dir.oGet(i))
                    }
                    i++
                }
                num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
                i = 0
                while (i < num) {
                    touch = clipModelList[i]
                    if (null == touch) {
                        i++
                        continue
                    }
                    if (touch.renderModelHandle != -1) {
                        numRenderModelTraces++
                        TraceRenderModel(trace, start, end, radius, trmAxis, touch)
                    } else {
                        numTranslations++
                        CollisionModel_local.collisionModelManager.Translation(
                            trace,
                            start,
                            end,
                            trm,
                            trmAxis,
                            contentMask,
                            touch.Handle(),
                            touch.origin,
                            touch.axis
                        )
                    }
                    if (trace.fraction < translationalTrace.fraction) {
                        translationalTrace = trace
                        translationalTrace.c.entityNum = touch.entity.entityNumber
                        translationalTrace.c.id = touch.id
                        if (translationalTrace.fraction == 0.0f) {
                            break
                        }
                    }
                    i++
                }
            } else {
                num = -1
            }
            endPosition.oSet(translationalTrace.endpos)
            endRotation = idRotation(rotation)
            endRotation.SetOrigin(endPosition)
            if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // rotational collision with world
                numRotations++
                CollisionModel_local.collisionModelManager.Rotation(
                    rotationalTrace,
                    endPosition,
                    endRotation,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    Vector.getVec3_origin(),
                    idMat3.Companion.getMat3_default()
                )
                rotationalTrace.c.entityNum =
                    if (rotationalTrace.fraction != 1.0f) Game_local.ENTITYNUM_WORLD else Game_local.ENTITYNUM_NONE
            } else {
//		memset( &rotationalTrace, 0, sizeof( rotationalTrace ) );
                rotationalTrace = trace_s()
                rotationalTrace.fraction = 1.0f
                rotationalTrace.endpos.oSet(endPosition)
                rotationalTrace.endAxis.oSet(trmAxis.times(rotation.ToMat3()))
            }
            if (rotationalTrace.fraction != 0.0f) {
                if (num == -1) {
                    traceBounds.FromBoundsRotation(trm.bounds, endPosition, trmAxis, endRotation)
                    num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
                }
                i = 0
                while (i < num) {
                    touch = clipModelList[i]
                    if (null == touch) {
                        i++
                        continue
                    }

                    // no rotational collision detection with render models
                    if (touch.renderModelHandle != -1) {
                        i++
                        continue
                    }
                    numRotations++
                    CollisionModel_local.collisionModelManager.Rotation(
                        trace,
                        endPosition,
                        endRotation,
                        trm,
                        trmAxis,
                        contentMask,
                        touch.Handle(),
                        touch.origin,
                        touch.axis
                    )
                    if (trace.fraction < rotationalTrace.fraction) {
                        rotationalTrace.oSet(trace)
                        rotationalTrace.c.entityNum = touch.entity.entityNumber
                        rotationalTrace.c.id = touch.id
                        if (rotationalTrace.fraction == 0.0f) {
                            break
                        }
                    }
                    i++
                }
            }
            if (rotationalTrace.fraction < 1.0f) {
                results.oSet(rotationalTrace)
            } else {
                results.oSet(translationalTrace)
                results.endAxis.oSet(rotationalTrace.endAxis)
            }
            results.fraction = Lib.Companion.Max(translationalTrace.fraction, rotationalTrace.fraction)
            return translationalTrace.fraction < 1.0f || rotationalTrace.fraction < 1.0f
        }

        fun Contacts(
            contacts: Array<contactInfo_t?>?, maxContacts: Int, start: idVec3?, dir: idVec6?, depth: Float,
            mdl: idClipModel?, trmAxis: idMat3?, contentMask: Int, passEntity: idEntity?
        ): Int {
            var i: Int
            var j: Int
            val num: Int
            var n: Int
            var numContacts: Int
            var touch: idClipModel
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            var traceBounds: idBounds? = idBounds()
            val trm: idTraceModel?
            trm = TraceModelForClipModel(mdl)
            numContacts = if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                this.numContacts++
                CollisionModel_local.collisionModelManager.Contacts(
                    contacts,
                    maxContacts,
                    start,
                    dir,
                    depth,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    Vector.getVec3_origin(),
                    idMat3.Companion.getMat3_default()
                )
            } else {
                0
            }
            i = 0
            while (i < numContacts) {
                contacts.get(i).entityNum = Game_local.ENTITYNUM_WORLD
                contacts.get(i).id = 0
                i++
            }
            if (numContacts >= maxContacts) {
                return numContacts
            }
            if (null == trm) {
                traceBounds = idBounds(start).Expand(depth)
            } else {
                traceBounds.FromTransformedBounds(trm.bounds, start, trmAxis)
                traceBounds.ExpandSelf(depth)
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }

                // no contacts with render models
                if (touch.renderModelHandle != -1) {
                    i++
                    continue
                }
                this.numContacts++
                val contactz = Arrays.copyOfRange(contacts, numContacts, contacts.size)
                n = CollisionModel_local.collisionModelManager.Contacts(
                    contactz, maxContacts - numContacts,
                    start, dir, depth, trm, trmAxis, contentMask,
                    touch.Handle(), touch.origin, touch.axis
                )
                j = 0
                while (j < n) {
                    contacts.get(numContacts) = contactz[j]
                    contacts.get(numContacts).entityNum = touch.entity.entityNumber
                    contacts.get(numContacts).id = touch.id
                    numContacts++
                    j++
                }
                if (numContacts >= maxContacts) {
                    break
                }
                i++
            }
            return numContacts
        }

        fun Contents(
            start: idVec3?,
            mdl: idClipModel?,
            trmAxis: idMat3?,
            contentMask: Int,
            passEntity: idEntity?
        ): Int {
            var i: Int
            val num: Int
            var contents: Int
            var touch: idClipModel
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val trm: idTraceModel?
            trm = TraceModelForClipModel(mdl)
            contents = if (null == passEntity || passEntity.entityNumber != Game_local.ENTITYNUM_WORLD) {
                // test world
                numContents++
                CollisionModel_local.collisionModelManager.Contents(
                    start,
                    trm,
                    trmAxis,
                    contentMask,
                    0,
                    Vector.getVec3_origin(),
                    idMat3.Companion.getMat3_default()
                )
            } else {
                0
            }
            if (null == trm) {
                traceBounds.oSet(0, start)
                traceBounds.oSet(1, start)
            } else if (trmAxis.IsRotated()) {
                traceBounds.FromTransformedBounds(trm.bounds, start, trmAxis)
            } else {
                traceBounds.oSet(0, trm.bounds.oGet(0).oPlus(start))
                traceBounds.oSet(1, trm.bounds.oGet(1).oPlus(start))
            }
            num = GetTraceClipModels(traceBounds, -1, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }

                // no contents test with render models
                if (touch.renderModelHandle != -1) {
                    i++
                    continue
                }

                // if the entity does not have any contents we are looking for
                if (touch.contents and contentMask == 0) {
                    i++
                    continue
                }

                // if the entity has no new contents flags
                if (touch.contents and contents == touch.contents) {
                    i++
                    continue
                }
                numContents++
                if (CollisionModel_local.collisionModelManager.Contents(
                        start,
                        trm,
                        trmAxis,
                        contentMask,
                        touch.Handle(),
                        touch.origin,
                        touch.axis
                    ) != 0
                ) {
                    contents = contents or (touch.contents and contentMask)
                }
                i++
            }
            return contents
        }

        // special case translations versus the rest of the world
        fun TracePoint(
            results: trace_s?,
            start: idVec3?,
            end: idVec3?,
            contentMask: Int,
            passEntity: idEntity?
        ): Boolean {
            Translation(results, start, end, null, idMat3.Companion.getMat3_identity(), contentMask, passEntity)
            return results.fraction < 1.0f
        }

        fun TraceBounds(
            results: trace_s?,
            start: idVec3?,
            end: idVec3?,
            bounds: idBounds?,
            contentMask: Int,
            passEntity: idEntity?
        ): Boolean {
            temporaryClipModel.LoadModel(idTraceModel(bounds))
            Translation(
                results,
                start,
                end,
                temporaryClipModel,
                idMat3.Companion.getMat3_identity(),
                contentMask,
                passEntity
            )
            return results.fraction < 1.0f
        }

        // clip versus a specific model
        fun TranslationModel(
            results: trace_s?,
            start: idVec3?,
            end: idVec3?,
            mdl: idClipModel?,
            trmAxis: idMat3?,
            contentMask: Int,    /*cmHandle_t*/
            model: Int,
            modelOrigin: idVec3?,
            modelAxis: idMat3?
        ) {
            val trm = TraceModelForClipModel(mdl)
            numTranslations++
            CollisionModel_local.collisionModelManager.Translation(
                results,
                start,
                end,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        fun RotationModel(
            results: trace_s?,
            start: idVec3?,
            rotation: idRotation?,
            mdl: idClipModel?,
            trmAxis: idMat3?,
            contentMask: Int,    /*cmHandle_t*/
            model: Int,
            modelOrigin: idVec3?,
            modelAxis: idMat3?
        ) {
            val trm = TraceModelForClipModel(mdl)
            numRotations++
            CollisionModel_local.collisionModelManager.Rotation(
                results,
                start,
                rotation,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        fun ContactsModel(
            contacts: Array<contactInfo_t?>?,
            maxContacts: Int,
            start: idVec3?,
            dir: idVec6?,
            depth: Float,
            mdl: idClipModel?,
            trmAxis: idMat3?,
            contentMask: Int,    /*cmHandle_t*/
            model: Int,
            modelOrigin: idVec3?,
            modelAxis: idMat3?
        ): Int {
            val trm = TraceModelForClipModel(mdl)
            numContacts++
            return CollisionModel_local.collisionModelManager.Contacts(
                contacts,
                maxContacts,
                start,
                dir,
                depth,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        fun ContentsModel(
            start: idVec3?, mdl: idClipModel?, trmAxis: idMat3?, contentMask: Int,
            /*cmHandle_t*/model: Int, modelOrigin: idVec3?, modelAxis: idMat3?
        ): Int {
            val trm = TraceModelForClipModel(mdl)
            numContents++
            return CollisionModel_local.collisionModelManager.Contents(
                start,
                trm,
                trmAxis,
                contentMask,
                model,
                modelOrigin,
                modelAxis
            )
        }

        // clip versus all entities but not the world
        fun TranslationEntities(
            results: trace_s?, start: idVec3?, end: idVec3?,
            mdl: idClipModel?, trmAxis: idMat3?, contentMask: Int, passEntity: idEntity?
        ) {
            var i: Int
            val num: Int
            var touch: idClipModel
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            val traceBounds = idBounds()
            val radius: Float
            val trace = trace_s()
            val trm: idTraceModel?
            if (Clip.TestHugeTranslation(results, mdl, start, end, trmAxis)) {
                return
            }
            trm = TraceModelForClipModel(mdl)
            results.fraction = 1.0f
            results.endpos.oSet(end)
            results.endAxis.oSet(trmAxis)
            radius = if (null == trm) {
                traceBounds.FromPointTranslation(start, end.oMinus(start))
                0.0f
            } else {
                traceBounds.FromBoundsTranslation(trm.bounds, start, trmAxis, end.oMinus(start))
                trm.bounds.GetRadius()
            }
            num = GetTraceClipModels(traceBounds, contentMask, passEntity, clipModelList)
            i = 0
            while (i < num) {
                touch = clipModelList[i]
                if (null == touch) {
                    i++
                    continue
                }
                if (touch.renderModelHandle != -1) {
                    numRenderModelTraces++
                    TraceRenderModel(trace, start, end, radius, trmAxis, touch)
                } else {
                    numTranslations++
                    CollisionModel_local.collisionModelManager.Translation(
                        trace, start, end, trm, trmAxis, contentMask,
                        touch.Handle(), touch.origin, touch.axis
                    )
                }
                if (trace.fraction < results.fraction) {
                    results.oSet(trace)
                    results.c.entityNum = touch.entity.entityNumber
                    results.c.id = touch.id
                    if (results.fraction == 0.0f) {
                        break
                    }
                }
                i++
            }
        }

        // get a contact feature
        fun GetModelContactFeature(
            contact: contactInfo_t?,
            clipModel: idClipModel?,
            winding: idFixedWinding?
        ): Boolean {
            var i: Int
            var   /*cmHandle_t*/handle: Int
            val start = idVec3()
            val end = idVec3()
            handle = -1
            winding.Clear()
            handle = if (clipModel == null) {
                0
            } else {
                if (clipModel.renderModelHandle != -1) {
                    winding.oPluSet(contact.point)
                    return true
                } else if (clipModel.traceModelIndex != -1) {
                    CollisionModel_local.collisionModelManager.SetupTrmModel(
                        idClipModel.GetCachedTraceModel(clipModel.traceModelIndex),
                        clipModel.material
                    )
                } else {
                    clipModel.collisionModelHandle
                }
            }

            // if contact with a collision model
            if (handle != -1) {
                when (contact.type) {
                    contactType_t.CONTACT_EDGE -> {

                        // the model contact feature is a collision model edge
                        CollisionModel_local.collisionModelManager.GetModelEdge(
                            handle,
                            contact.modelFeature,
                            start,
                            end
                        )
                        winding.oPluSet(start)
                        winding.oPluSet(end)
                    }
                    contactType_t.CONTACT_MODELVERTEX -> {

                        // the model contact feature is a collision model vertex
                        CollisionModel_local.collisionModelManager.GetModelVertex(handle, contact.modelFeature, start)
                        winding.oPluSet(start)
                    }
                    contactType_t.CONTACT_TRMVERTEX -> {

                        // the model contact feature is a collision model polygon
                        CollisionModel_local.collisionModelManager.GetModelPolygon(
                            handle,
                            contact.modelFeature,
                            winding
                        ) //TODO:is this function necessary?
                    }
                }
            }

            // transform the winding to world space
            if (clipModel != null) {
                i = 0
                while (i < winding.GetNumPoints()) {
                    winding.oGet(i).ToVec3_oMulSet(clipModel.axis)
                    winding.oGet(i).ToVec3_oPluSet(clipModel.origin)
                    i++
                }
            }
            return true
        }

        // get entities/clip models within or touching the given bounds
        fun EntitiesTouchingBounds(
            bounds: idBounds?,
            contentMask: Int,
            entityList: Array<idEntity?>?,
            maxCount: Int
        ): Int {
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            var i: Int
            var j: Int
            val count: Int
            var entCount: Int
            count = ClipModelsTouchingBounds(bounds, contentMask, clipModelList, Game_local.MAX_GENTITIES)
            entCount = 0
            i = 0
            while (i < count) {

                // entity could already be in the list because an entity can use multiple clip models
                j = 0
                while (j < entCount) {
                    if (entityList.get(j) === clipModelList[i].entity) {
                        break
                    }
                    j++
                }
                if (j >= entCount) {
                    if (entCount >= maxCount) {
                        Game_local.gameLocal.Warning("idClip::EntitiesTouchingBounds: max count")
                        return entCount
                    }
                    entityList.get(entCount) = clipModelList[i].entity
                    entCount++
                }
                i++
            }
            return entCount
        }

        fun ClipModelsTouchingBounds(
            bounds: idBounds?,
            contentMask: Int,
            clipModelList: Array<idClipModel?>?,
            maxCount: Int
        ): Int {
            val parms = listParms_s()
            if (bounds.oGet(0, 0) > bounds.oGet(1, 0) || bounds.oGet(0, 1) > bounds.oGet(1, 1) || bounds.oGet(
                    0,
                    2
                ) > bounds.oGet(1, 2)
            ) {
                // we should not go through the tree for degenerate or backwards bounds
                assert(false)
                return 0
            }
            parms.bounds.oSet(0, bounds.oGet(0).oMinus(Clip.vec3_boxEpsilon))
            parms.bounds.oSet(1, bounds.oGet(1).oPlus(Clip.vec3_boxEpsilon))
            parms.contentMask = contentMask
            parms.list = clipModelList
            parms.count = 0
            parms.maxCount = maxCount
            touchCount++
            ClipModelsTouchingBounds_r(clipSectors.get(0), parms)
            return parms.count
        }

        fun GetWorldBounds(): idBounds? {
            return worldBounds
        }

        fun DefaultClipModel(): idClipModel? {
            return defaultClipModel
        }

        // stats and debug drawing
        fun PrintStatistics() {
            Game_local.gameLocal.Printf(
                "t = %-3d, r = %-3d, m = %-3d, render = %-3d, contents = %-3d, contacts = %-3d\n",
                numTranslations, numRotations, numMotions, numRenderModelTraces, numContents, numContacts
            )
            numContacts = 0
            numContents = numContacts
            numRenderModelTraces = numContents
            numMotions = numRenderModelTraces
            numTranslations = numMotions
            numRotations = numTranslations
        }

        fun DrawClipModels(eye: idVec3?, radius: Float, passEntity: idEntity?) {
            var i: Int
            val num: Int
            val bounds: idBounds?
            val clipModelList = arrayOfNulls<idClipModel?>(Game_local.MAX_GENTITIES)
            var clipModel: idClipModel?
            bounds = idBounds(eye).Expand(radius)
            num = ClipModelsTouchingBounds(bounds, -1, clipModelList, Game_local.MAX_GENTITIES)
            i = 0
            while (i < num) {
                clipModel = clipModelList[i]
                if (clipModel.GetEntity() === passEntity) {
                    i++
                    continue
                }
                if (clipModel.renderModelHandle != -1) {
                    Game_local.gameRenderWorld.DebugBounds(Lib.Companion.colorCyan, clipModel.GetAbsBounds())
                } else {
                    CollisionModel_local.collisionModelManager.DrawModel(
                        clipModel.Handle(),
                        clipModel.GetOrigin(),
                        clipModel.GetAxis(),
                        eye,
                        radius
                    )
                }
                i++
            }
        }

        fun DrawModelContactFeature(contact: contactInfo_t?, clipModel: idClipModel?, lifetime: Int): Boolean {
            var i: Int
            val axis: idMat3?
            val winding = idFixedWinding()
            if (!GetModelContactFeature(contact, clipModel, winding)) {
                return false
            }
            axis = contact.normal.ToMat3()
            if (winding.GetNumPoints() == 1) {
                Game_local.gameRenderWorld.DebugLine(
                    Lib.Companion.colorCyan,
                    winding.oGet(0).ToVec3(),
                    winding.oGet(0).ToVec3().oPlus(axis.oGet(0).times(2.0f)),
                    lifetime
                )
                Game_local.gameRenderWorld.DebugLine(
                    Lib.Companion.colorWhite,
                    winding.oGet(0).ToVec3().oMinus( /*- 1.0f * */axis.oGet(1)),
                    winding.oGet(0).ToVec3().oPlus( /*+ 1.0f */axis.oGet(1)),
                    lifetime
                )
                Game_local.gameRenderWorld.DebugLine(
                    Lib.Companion.colorWhite,
                    winding.oGet(0).ToVec3().oMinus( /*- 1.0f * */axis.oGet(2)),
                    winding.oGet(0).ToVec3().oPlus( /*+ 1.0f */axis.oGet(2)),
                    lifetime
                )
            } else {
                i = 0
                while (i < winding.GetNumPoints()) {
                    Game_local.gameRenderWorld.DebugLine(
                        Lib.Companion.colorCyan,
                        winding.oGet(i).ToVec3(),
                        winding.oGet((i + 1) % winding.GetNumPoints()).ToVec3(),
                        lifetime
                    )
                    i++
                }
            }
            axis.oSet(0, axis.oGet(0).oNegative())
            axis.oSet(2, axis.oGet(2).oNegative())
            Game_local.gameRenderWorld.DrawText(
                contact.material.GetName(),
                winding.GetCenter().oMinus(axis.oGet(2).times(4.0f)),
                0.1f,
                Lib.Companion.colorWhite,
                axis,
                1,
                5000
            )
            return true
        }

        /*
         ===============
         idClip::CreateClipSectors_r

         Builds a uniformly subdivided tree for the given world size
         ===============
         */
        private fun CreateClipSectors_r(depth: Int, bounds: idBounds?, maxSector: idVec3?): clipSector_s? {
            var i: Int
            val anode: clipSector_s?
            val size = idVec3()
            val front: idBounds
            val back: idBounds
            clipSectors.get(numClipSectors++) = clipSector_s()
            anode = clipSectors.get(numClipSectors++)
            if (depth == Clip.MAX_SECTOR_DEPTH) {
                anode.axis = -1
                anode.children.get(1) = null
                anode.children.get(0) = anode.children.get(1)
                i = 0
                while (i < 3) {
                    if (bounds.oGet(1, i) - bounds.oGet(0, i) > maxSector.oGet(i)) {
                        maxSector.oSet(i, bounds.oGet(1, i) - bounds.oGet(0, i))
                    }
                    i++
                }
                return anode
            }
            size.oSet(bounds.oGet(1).oMinus(bounds.oGet(0)))
            if (size.oGet(0) >= size.oGet(1) && size.oGet(0) >= size.oGet(2)) {
                anode.axis = 0
            } else if (size.oGet(1) >= size.oGet(0) && size.oGet(1) >= size.oGet(2)) {
                anode.axis = 1
            } else {
                anode.axis = 2
            }
            anode.dist = 0.5f * (bounds.oGet(1, anode.axis) + bounds.oGet(0, anode.axis))
            front = idBounds(bounds)
            back = idBounds(bounds)
            front.oSet(0, anode.axis, back.oSet(1, anode.axis, anode.dist))
            anode.children.get(0) = CreateClipSectors_r(depth + 1, front, maxSector)
            anode.children.get(1) = CreateClipSectors_r(depth + 1, back, maxSector)
            return anode
        }

        private fun ClipModelsTouchingBounds_r(node: clipSector_s?, parms: listParms_s?) {
            var node = node
            while (node.axis != -1) {
                node = if (parms.bounds.oGet(0, node.axis) > node.dist) {
                    node.children.get(0)
                } else if (parms.bounds.oGet(1, node.axis) < node.dist) {
                    node.children.get(1)
                } else {
                    ClipModelsTouchingBounds_r(node.children.get(0), parms)
                    node.children.get(1)
                }
            }
            var link = node.clipLinks
            while (link != null) {
                val check = link.clipModel

                // if the clip model is enabled
                if (!check.enabled) {
                    link = link.nextInSector
                    continue
                }

                // avoid duplicates in the list
                if (check.touchCount == touchCount) {
                    link = link.nextInSector
                    continue
                }

                // if the clip model does not have any contents we are looking for
                if (0 == check.contents and parms.contentMask) {
                    link = link.nextInSector
                    continue
                }

                // if the bounds really do overlap
                if (check.absBounds.oGet(0, 0) > parms.bounds.oGet(1, 0) || check.absBounds.oGet(
                        1,
                        0
                    ) < parms.bounds.oGet(0, 0) || check.absBounds.oGet(0, 1) > parms.bounds.oGet(
                        1,
                        1
                    ) || check.absBounds.oGet(1, 1) < parms.bounds.oGet(0, 1) || check.absBounds.oGet(
                        0,
                        2
                    ) > parms.bounds.oGet(1, 2) || check.absBounds.oGet(1, 2) < parms.bounds.oGet(0, 2)
                ) {
                    link = link.nextInSector
                    continue
                }
                if (parms.count >= parms.maxCount) {
                    Game_local.gameLocal.Warning("idClip::ClipModelsTouchingBounds_r: max count")
                    return
                }
                check.touchCount = touchCount
                parms.list.get(parms.count) = check
                parms.count++
                link = link.nextInSector
            }
        }

        private fun TraceModelForClipModel(mdl: idClipModel?): idTraceModel? {
            return if (null == mdl) {
                null
            } else {
                if (!mdl.IsTraceModel()) {
                    if (mdl.GetEntity() != null) {
                        idGameLocal.Companion.Error(
                            "TraceModelForClipModel: clip model %d on '%s' is not a trace model\n",
                            mdl.GetId(),
                            mdl.GetEntity().name
                        )
                    } else {
                        idGameLocal.Companion.Error(
                            "TraceModelForClipModel: clip model %d is not a trace model\n",
                            mdl.GetId()
                        )
                    }
                }
                idClipModel.GetCachedTraceModel(mdl.traceModelIndex)
            }
        }

        /*
         ====================
         idClip::GetTraceClipModels

         an ent will be excluded from testing if:
         cm->entity == passEntity ( don't clip against the pass entity )
         cm->entity == passOwner ( missiles don't clip with owner )
         cm->owner == passEntity ( don't interact with your own missiles )
         cm->owner == passOwner ( don't interact with other missiles from same owner )
         ====================
         */
        private fun GetTraceClipModels(
            bounds: idBounds?,
            contentMask: Int,
            passEntity: idEntity?,
            clipModelList: Array<idClipModel?>?
        ): Int {
            var i: Int
            val num: Int
            var cm: idClipModel?
            val passOwner: idEntity?
            num = ClipModelsTouchingBounds(bounds, contentMask, clipModelList, Game_local.MAX_GENTITIES)
            if (null == passEntity) {
                return num
            }
            passOwner = if (passEntity.GetPhysics().GetNumClipModels() > 0) {
                passEntity.GetPhysics().GetClipModel().GetOwner()
            } else {
                null
            }
            i = 0
            while (i < num) {
                cm = clipModelList.get(i)

                // check if we should ignore this entity
                if (cm.entity === passEntity) {
                    clipModelList.get(i) = null // don't clip against the pass entity
                } else if (cm.entity === passOwner) {
                    clipModelList.get(i) = null // missiles don't clip with their owner
                } else if (cm.owner != null) {
                    if (cm.owner === passEntity) {
                        clipModelList.get(i) = null // don't clip against own missiles
                    } else if (cm.owner === passOwner) {
                        clipModelList.get(i) = null // don't clip against other missiles from same owner
                    }
                }
                i++
            }
            return num
        }

        private fun TraceRenderModel(
            trace: trace_s?,
            start: idVec3?,
            end: idVec3?,
            radius: Float,
            axis: idMat3?,
            touch: idClipModel?
        ) {
            trace.fraction = 1.0f

            // if the trace is passing through the bounds
            if (touch.absBounds.Expand(radius).LineIntersection(start, end)) {
                val modelTrace = modelTrace_s()

                // test with exact render model and modify trace_t structure accordingly
                if (Game_local.gameRenderWorld.ModelTrace(modelTrace, touch.renderModelHandle, start, end, radius)) {
                    trace.fraction = modelTrace.fraction
                    trace.endAxis.oSet(axis)
                    trace.endpos.oSet(modelTrace.point)
                    trace.c.normal.oSet(modelTrace.normal)
                    trace.c.dist = modelTrace.point.times(modelTrace.normal)
                    trace.c.point.oSet(modelTrace.point)
                    trace.c.type = contactType_t.CONTACT_TRMVERTEX
                    trace.c.modelFeature = 0
                    trace.c.trmFeature = 0
                    trace.c.contents = modelTrace.material.GetContentFlags()
                    trace.c.material = modelTrace.material
                    // NOTE: trace.c.id will be the joint number
                    touch.id = Clip.JOINT_HANDLE_TO_CLIPMODEL_ID(modelTrace.jointNumber)
                }
            }
        }

        /*
         ====================
         idClip::ClipModelsTouchingBounds_r
         ====================
         */
        private class listParms_s {
            var bounds: idBounds?
            var contentMask = 0
            var count = 0
            var list: Array<idClipModel?>?
            var maxCount = 0

            init {
                bounds = idBounds()
            }
        }

        //
        //
        init {
            worldBounds = idBounds() //worldBounds.Zero();
            numContacts = 0
            numContents = numContacts
            numRenderModelTraces = numContents
            numMotions = numRenderModelTraces
            numTranslations = numMotions
            numRotations = numTranslations
        }
    }
}