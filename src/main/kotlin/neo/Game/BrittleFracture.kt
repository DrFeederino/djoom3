package neo.Game

import neo.CM.CollisionModel
import neo.CM.CollisionModel.trace_s
import neo.Game.*
import neo.Game.Entity.idEntity
import neo.Game.FX.idEntityFx
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t1
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Game_local.gameSoundChannel_t
import neo.Game.Game_local.idGameLocal
import neo.Game.Physics.Clip.idClipModel
import neo.Game.Physics.Physics_RigidBody.idPhysics_RigidBody
import neo.Game.Physics.Physics_StaticMulti.idPhysics_StaticMulti
import neo.Renderer.Material
import neo.Renderer.Model.idRenderModel
import neo.Renderer.Model.modelSurface_s
import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.ModelManager
import neo.Renderer.RenderWorld
import neo.Renderer.RenderWorld.*
import neo.Sound.snd_shader.idSoundShader
import neo.TempDump
import neo.framework.DeclEntityDef.idDeclEntityDef
import neo.framework.DeclManager
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.BitMsg.idBitMsg
import neo.idlib.Lib
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.CFloat
import neo.idlib.containers.CInt
import neo.idlib.containers.List.idList
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.geometry.TraceModel.idTraceModel
import neo.idlib.geometry.Winding
import neo.idlib.geometry.Winding.idFixedWinding
import neo.idlib.geometry.Winding.idWinding
import neo.idlib.math.Math_h
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Simd
import neo.idlib.math.Vector.idVec2
import neo.idlib.math.Vector.idVec3
import neo.idlib.math.Vector.idVec4
import java.nio.*

/**
 *
 */
object BrittleFracture {
    /*
     ===============================================================================

     B-rep Brittle Fracture - Static entity using the boundary representation
     of the render model which can fracture.

     ===============================================================================
     */
    //
    const val SHARD_ALIVE_TIME = 5000
    const val SHARD_FADE_START = 2000

    //
    val brittleFracture_SnapshotName: String? = "_BrittleFracture_Snapshot_"

    class shard_s {
        var atEdge = false
        var clipModel: idClipModel? = null
        val decals: idList<idFixedWinding?>? = idList<Any?>()
        var droppedTime = 0
        val edgeHasNeighbour: idList<Boolean?>? = idList<Any?>()
        var islandNum = 0
        val neighbours: idList<shard_s?>? = idList<Any?>()
        var physicsObj: idPhysics_RigidBody? = null
        var winding: idFixedWinding? = null
    }

    //
    class idBrittleFracture : idEntity() {
        companion object {
            //
            // enum {
            val EVENT_PROJECT_DECAL: Int = idEntity.Companion.EVENT_MAXEVENTS
            val EVENT_MAXEVENTS = 2 + EVENT_PROJECT_DECAL
            val EVENT_SHATTER = 1 + EVENT_PROJECT_DECAL

            // public CLASS_PROTOTYPE( idBrittleFracture );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>?>? = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>?>? {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idEntity.Companion.getEventCallBacks())
                eventCallbacks[Entity.EV_Activate] =
                    eventCallback_t1<idBrittleFracture?> { obj: T?, activator: idEventArg<*>? ->
                        neo.Game.obj.Event_Activate(neo.Game.activator)
                    } as eventCallback_t1<idBrittleFracture?>
                eventCallbacks[Entity.EV_Touch] =
                    eventCallback_t2<idBrittleFracture?> { obj: T?, _other: idEventArg<*>? ->
                        neo.Game.obj.Event_Touch(neo.Game._other)
                    } as eventCallback_t2<idBrittleFracture?>
            }
        }

        // };
        //        
        private var angularVelocityScale: Float
        private var bouncyness: Float
        private val bounds: idBounds
        private var changed: Boolean
        private var decalMaterial: idMaterial?
        private var decalSize: Float
        private var density: Float
        private var disableFracture: Boolean
        private var friction: Float
        private val fxFracture: idStr?

        //
        // for rendering
        private var lastRenderEntityUpdate: Int
        private var linearVelocityScale: Float

        //
        // setttings
        private var material: idMaterial?
        private var maxShardArea: Float
        private var maxShatterRadius: Float
        private var minShatterRadius: Float

        //
        // state
        private val physicsObj: idPhysics_StaticMulti?
        private var shardMass: Float
        private val shards: idList<shard_s?>?
        override fun Save(savefile: idSaveGame) {
            var i: Int
            var j: Int
            savefile.WriteInt(health)
            val flags = fl
            Lib.Companion.LittleBitField(flags)
            savefile.Write(flags)

            // setttings
            savefile.WriteMaterial(material)
            savefile.WriteMaterial(decalMaterial)
            savefile.WriteFloat(decalSize)
            savefile.WriteFloat(maxShardArea)
            savefile.WriteFloat(maxShatterRadius)
            savefile.WriteFloat(minShatterRadius)
            savefile.WriteFloat(linearVelocityScale)
            savefile.WriteFloat(angularVelocityScale)
            savefile.WriteFloat(shardMass)
            savefile.WriteFloat(density)
            savefile.WriteFloat(friction)
            savefile.WriteFloat(bouncyness)
            savefile.WriteString(fxFracture)

            // state
            savefile.WriteBounds(bounds)
            savefile.WriteBool(disableFracture)
            savefile.WriteInt(lastRenderEntityUpdate)
            savefile.WriteBool(changed)
            savefile.WriteStaticObject(physicsObj)
            savefile.WriteInt(shards.Num())
            i = 0
            while (i < shards.Num()) {
                savefile.WriteWinding(shards.get(i).winding)
                savefile.WriteInt(shards.get(i).decals.Num())
                j = 0
                while (j < shards.get(i).decals.Num()) {
                    savefile.WriteWinding(shards.get(i).decals.get(j))
                    j++
                }
                savefile.WriteInt(shards.get(i).neighbours.Num())
                j = 0
                while (j < shards.get(i).neighbours.Num()) {
                    val index = shards.FindIndex(shards.get(i).neighbours.get(j))
                    assert(index != -1)
                    savefile.WriteInt(index)
                    j++
                }
                savefile.WriteInt(shards.get(i).edgeHasNeighbour.Num())
                j = 0
                while (j < shards.get(i).edgeHasNeighbour.Num()) {
                    savefile.WriteBool(shards.get(i).edgeHasNeighbour.get(j))
                    j++
                }
                savefile.WriteInt(shards.get(i).droppedTime)
                savefile.WriteInt(shards.get(i).islandNum)
                savefile.WriteBool(shards.get(i).atEdge)
                savefile.WriteStaticObject(shards.get(i).physicsObj)
                i++
            }
        }

        override fun Restore(savefile: idRestoreGame) {
            var i: Int
            var j: Int
            val num = CInt()
            renderEntity.hModel = ModelManager.renderModelManager.AllocModel()
            renderEntity.hModel.InitEmpty(BrittleFracture.brittleFracture_SnapshotName)
            renderEntity.callback = BrittleFracture.idBrittleFracture.ModelCallback.Companion.getInstance()
            renderEntity.noShadow = true
            renderEntity.noSelfShadow = true
            renderEntity.noDynamicInteractions = false
            health = savefile.ReadInt()
            savefile.Read(fl)
            Lib.Companion.LittleBitField(fl)

            // setttings
            savefile.ReadMaterial(material)
            savefile.ReadMaterial(decalMaterial)
            decalSize = savefile.ReadFloat()
            maxShardArea = savefile.ReadFloat()
            maxShatterRadius = savefile.ReadFloat()
            minShatterRadius = savefile.ReadFloat()
            linearVelocityScale = savefile.ReadFloat()
            angularVelocityScale = savefile.ReadFloat()
            shardMass = savefile.ReadFloat()
            density = savefile.ReadFloat()
            friction = savefile.ReadFloat()
            bouncyness = savefile.ReadFloat()
            savefile.ReadString(fxFracture)

            // state
            savefile.ReadBounds(bounds)
            disableFracture = savefile.ReadBool()
            lastRenderEntityUpdate = savefile.ReadInt()
            changed = savefile.ReadBool()
            savefile.ReadStaticObject(physicsObj)
            RestorePhysics(physicsObj)
            savefile.ReadInt(num)
            shards.SetNum(num._val)
            i = 0
            while (i < num._val) {
                shards.set(i, shard_s())
                i++
            }
            i = 0
            while (i < num._val) {
                savefile.ReadWinding(shards.get(i).winding)
                j = savefile.ReadInt()
                shards.get(i).decals.SetNum(j)
                j = 0
                while (j < shards.get(i).decals.Num()) {
                    shards.get(i).decals.set(j, idFixedWinding())
                    savefile.ReadWinding(shards.get(i).decals.get(j)) //TODO:pointer of begin range?
                    j++
                }
                j = savefile.ReadInt()
                shards.get(i).neighbours.SetNum(j)
                j = 0
                while (j < shards.get(i).neighbours.Num()) {
                    val index = CInt()
                    savefile.ReadInt(index)
                    assert(index._val != -1)
                    shards.get(i).neighbours.set(j, shards.get(index._val))
                    j++
                }
                j = savefile.ReadInt()
                shards.get(i).edgeHasNeighbour.SetNum(j)
                j = 0
                while (j < shards.get(i).edgeHasNeighbour.Num()) {
                    shards.get(i).edgeHasNeighbour.set(j, savefile.ReadBool())
                    j++
                }
                shards.get(i).droppedTime = savefile.ReadInt()
                shards.get(i).islandNum = savefile.ReadInt()
                shards.get(i).atEdge = savefile.ReadBool()
                savefile.ReadStaticObject(shards.get(i).physicsObj)
                if (shards.get(i).droppedTime < 0) {
                    shards.get(i).clipModel = physicsObj.GetClipModel(i)
                } else {
                    shards.get(i).clipModel = shards.get(i).physicsObj.GetClipModel()
                }
                i++
            }
        }

        override fun Spawn() {
            super.Spawn()
            val d = CFloat()
            val f = CFloat()
            val b = CFloat()

            // get shard properties
            decalMaterial = DeclManager.declManager.FindMaterial(spawnArgs.GetString("mtr_decal"))
            decalSize = spawnArgs.GetFloat("decalSize", "40")
            maxShardArea = spawnArgs.GetFloat("maxShardArea", "200")
            maxShardArea = idMath.ClampFloat(100f, 10000f, maxShardArea)
            maxShatterRadius = spawnArgs.GetFloat("maxShatterRadius", "40")
            minShatterRadius = spawnArgs.GetFloat("minShatterRadius", "10")
            linearVelocityScale = spawnArgs.GetFloat("linearVelocityScale", "0.1")
            angularVelocityScale = spawnArgs.GetFloat("angularVelocityScale", "40")
            fxFracture.set(spawnArgs.GetString("fx"))

            // get rigid body properties
            shardMass = spawnArgs.GetFloat("shardMass", "20")
            shardMass = idMath.ClampFloat(0.001f, 1000.0f, shardMass)
            spawnArgs.GetFloat("density", "0.1", d)
            density = idMath.ClampFloat(0.001f, 1000.0f, d._val)
            spawnArgs.GetFloat("friction", "0.4", f)
            friction = idMath.ClampFloat(0.0f, 1.0f, f._val)
            spawnArgs.GetFloat("bouncyness", "0.01", b)
            bouncyness = idMath.ClampFloat(0.0f, 1.0f, b._val)
            disableFracture = spawnArgs.GetBool("disableFracture", "0")
            health = spawnArgs.GetInt("health", "40")
            fl.takedamage = true

            // FIXME: set "bleed" so idProjectile calls AddDamageEffect
            spawnArgs.SetBool("bleed", true)
            CreateFractures(renderEntity.hModel)
            FindNeighbours()
            renderEntity.hModel = ModelManager.renderModelManager.AllocModel()
            renderEntity.hModel.InitEmpty(BrittleFracture.brittleFracture_SnapshotName)
            renderEntity.callback = BrittleFracture.idBrittleFracture.ModelCallback.Companion.getInstance()
            renderEntity.noShadow = true
            renderEntity.noSelfShadow = true
            renderEntity.noDynamicInteractions = false
        }

        override fun Present() {

            // don't present to the renderer if the entity hasn't changed
            if (0 == thinkFlags and Entity.TH_UPDATEVISUALS) {
                return
            }
            BecomeInactive(Entity.TH_UPDATEVISUALS)
            renderEntity.bounds.set(bounds)
            renderEntity.origin.Zero()
            renderEntity.axis.Identity()

            // force an update because the bounds/origin/axis may stay the same while the model changes
            renderEntity.forceUpdate = 1 //true;

            // add to refresh list
            if (modelDefHandle == -1) {
                modelDefHandle = Game_local.gameRenderWorld.AddEntityDef(renderEntity)
            } else {
                Game_local.gameRenderWorld.UpdateEntityDef(modelDefHandle, renderEntity)
            }
            changed = true
        }

        override fun Think() {
            var i: Int
            val startTime: Int
            val endTime: Int
            var droppedTime: Int
            var shard: shard_s?
            var atRest = true
            var fading = false

            // remove overdue shards
            i = 0
            while (i < shards.Num()) {
                droppedTime = shards.get(i).droppedTime
                if (droppedTime != -1) {
                    if (Game_local.gameLocal.time - droppedTime > BrittleFracture.SHARD_ALIVE_TIME) {
                        RemoveShard(i)
                        i--
                    }
                    fading = true
                }
                i++
            }

            // remove the entity when nothing is visible
            if (0 == shards.Num()) {
                PostEventMS(Class.EV_Remove, 0)
                return
            }
            if (thinkFlags and Entity.TH_PHYSICS != 0) {
                startTime = Game_local.gameLocal.previousTime
                endTime = Game_local.gameLocal.time

                // run physics on shards
                i = 0
                while (i < shards.Num()) {
                    shard = shards.get(i)
                    if (shard.droppedTime == -1) {
                        i++
                        continue
                    }
                    shard.physicsObj.Evaluate(endTime - startTime, endTime)
                    if (!shard.physicsObj.IsAtRest()) {
                        atRest = false
                    }
                    i++
                }
                if (atRest) {
                    BecomeInactive(Entity.TH_PHYSICS)
                } else {
                    BecomeActive(Entity.TH_PHYSICS)
                }
            }
            if (!atRest || bounds.IsCleared()) {
                bounds.Clear()
                i = 0
                while (i < shards.Num()) {
                    bounds.AddBounds(shards.get(i).clipModel.GetAbsBounds())
                    i++
                }
            }
            if (fading) {
                BecomeActive(Entity.TH_UPDATEVISUALS or Entity.TH_THINK)
            } else {
                BecomeInactive(Entity.TH_THINK)
            }
            RunPhysics()
            Present()
        }

        override fun ApplyImpulse(ent: idEntity?, id: Int, point: idVec3, impulse: idVec3) {
            if (id < 0 || id >= shards.Num()) {
                return
            }
            if (shards.get(id).droppedTime != -1) {
                shards.get(id).physicsObj.ApplyImpulse(0, point, impulse)
            } else if (health <= 0 && !disableFracture) {
                Shatter(point, impulse, Game_local.gameLocal.time)
            }
        }

        override fun AddForce(ent: idEntity?, id: Int, point: idVec3, force: idVec3) {
            if (id < 0 || id >= shards.Num()) {
                return
            }
            if (shards.get(id).droppedTime != -1) {
                shards.get(id).physicsObj.AddForce(0, point, force)
            } else if (health <= 0 && !disableFracture) {
                Shatter(point, force, Game_local.gameLocal.time)
            }
        }

        override fun AddDamageEffect(collision: trace_s?, velocity: idVec3, damageDefName: String?) {
            if (!disableFracture) {
                ProjectDecal(collision.c.point, collision.c.normal, Game_local.gameLocal.time, damageDefName)
            }
        }

        override fun Killed(inflictor: idEntity?, attacker: idEntity?, damage: Int, dir: idVec3, location: Int) {
            if (!disableFracture) {
                ActivateTargets(this)
                Break()
            }
        }

        fun ProjectDecal(point: idVec3, dir: idVec3, time: Int, damageDefName: String?) {
            var i: Int
            var j: Int
            var bits: Int
            var clipBits: Int
            val a: Float
            val c: Float
            val s: Float
            val st: Array<idVec2> = idVec2.Companion.generateArray(Winding.MAX_POINTS_ON_WINDING)
            val origin = idVec3()
            var axis: idMat3 = idMat3()
            val axisTemp = idMat3()
            val textureAxis: Array<idPlane> = idPlane.Companion.generateArray(2)
            if (Game_local.gameLocal.isServer) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.BeginWriting()
                msg.WriteFloat(point.get(0))
                msg.WriteFloat(point.get(1))
                msg.WriteFloat(point.get(2))
                msg.WriteFloat(dir.get(0))
                msg.WriteFloat(dir.get(1))
                msg.WriteFloat(dir.get(2))
                ServerSendEvent(EVENT_PROJECT_DECAL, msg, true, -1)
            }
            if (time >= Game_local.gameLocal.time) {
                // try to get the sound from the damage def
                val damageDef: idDeclEntityDef?
                var sndShader: idSoundShader? = null
                if (damageDefName != null) {
                    damageDef = Game_local.gameLocal.FindEntityDef(damageDefName, false)
                    if (damageDef != null) {
                        sndShader = DeclManager.declManager.FindSound(damageDef.dict.GetString("snd_shatter", ""))
                    }
                }
                if (sndShader != null) {
                    StartSoundShader(sndShader, TempDump.etoi(gameSoundChannel_t.SND_CHANNEL_ANY), 0, false, null)
                } else {
                    StartSound("snd_bullethole", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false, null)
                }
            }
            a = Game_local.gameLocal.random.RandomFloat() * idMath.TWO_PI
            c = Math.cos(a.toDouble()).toFloat()
            s = -Math.sin(a.toDouble()).toFloat()
            axis.set(2, dir.oNegative())
            axis.get(2).Normalize()
            axis.get(2).NormalVectors(axisTemp.get(0), axisTemp.get(1))
            axis.set(0, axisTemp.get(0).times(c).oPlus(axisTemp.get(1).times(s)))
            axis.set(1, axisTemp.get(0).times(s).oPlus(axisTemp.get(1).times(-c)))
            textureAxis[0].set(axis.get(0).times(1.0f / decalSize))
            textureAxis[0].set(3, -point.times(textureAxis[0].Normal()) + 0.5f)
            textureAxis[1].set(axis.get(1).times(1.0f / decalSize))
            textureAxis[1].set(3, -point.times(textureAxis[1].Normal()) + 0.5f)
            i = 0
            while (i < shards.Num()) {
                val winding = shards.get(i).winding
                origin.set(shards.get(i).clipModel.GetOrigin())
                axis = shards.get(i).clipModel.GetAxis()
                var d0: Float
                var d1: Float
                clipBits = -1
                j = 0
                while (j < winding.GetNumPoints()) {
                    val p = idVec3(origin.oPlus(winding.get(j).ToVec3().times(axis)))
                    d0 = textureAxis[0].Distance(p)
                    st[j].x = d0
                    d1 = textureAxis[1].Distance(p)
                    st[j].y = d1
                    bits = Math_h.FLOATSIGNBITSET(d0)
                    d0 = 1.0f - d0
                    bits = bits or (Math_h.FLOATSIGNBITSET(d1) shl 2)
                    d1 = 1.0f - d1
                    bits = bits or (Math_h.FLOATSIGNBITSET(d0) shl 1)
                    bits = bits or (Math_h.FLOATSIGNBITSET(d1) shl 3)
                    clipBits = clipBits and bits
                    j++
                }
                if (clipBits != 0) {
                    i++
                    continue
                }
                val decal = idFixedWinding()
                shards.get(i).decals.Append(decal)
                decal.SetNumPoints(winding.GetNumPoints())
                j = 0
                while (j < winding.GetNumPoints()) {
                    decal.get(j).set(winding.get(j).ToVec3()) //TODO:double check this.
                    decal.get(j).s = st[j].x
                    decal.get(j).t = st[j].y
                    j++
                }
                i++
            }
            BecomeActive(Entity.TH_UPDATEVISUALS)
        }

        fun IsBroken(): Boolean {
            return fl.takedamage == false
        }

        override fun ClientPredictionThink() {
            // only think forward because the state is not synced through snapshots
            if (!Game_local.gameLocal.isNewFrame) {
                return
            }
            Think()
        }

        override fun ClientReceiveEvent(event: Int, time: Int, msg: idBitMsg?): Boolean {
            val point = idVec3()
            val dir = idVec3()
            return when (event) {
                EVENT_PROJECT_DECAL -> {
                    point.set(0, msg.ReadFloat())
                    point.set(1, msg.ReadFloat())
                    point.set(2, msg.ReadFloat())
                    dir.set(0, msg.ReadFloat())
                    dir.set(1, msg.ReadFloat())
                    dir.set(2, msg.ReadFloat())
                    ProjectDecal(point, dir, time, null)
                    true
                }
                EVENT_SHATTER -> {
                    point.set(0, msg.ReadFloat())
                    point.set(1, msg.ReadFloat())
                    point.set(2, msg.ReadFloat())
                    dir.set(0, msg.ReadFloat())
                    dir.set(1, msg.ReadFloat())
                    dir.set(2, msg.ReadFloat())
                    Shatter(point, dir, time)
                    true
                }
                else -> {
                    super.ClientReceiveEvent(event, time, msg)
                }
            }
            //            return false;
        }

        override fun UpdateRenderEntity(renderEntity: renderEntity_s, renderView: renderView_s?): Boolean {
            var i: Int
            var j: Int
            var k: Int
            var n: Int
            var msec: Int
            var numTris: Int
            var numDecalTris: Int
            var fade: Float
            var   /*dword*/packedColor: Int
            val tris: srfTriangles_s?
            val decalTris: srfTriangles_s?
            var surface: modelSurface_s
            var v: idDrawVert?
            val plane = idPlane()
            var tangents: idMat3

            // this may be triggered by a model trace or other non-view related source,
            // to which we should look like an empty model
            if (null == renderView) {
                return false
            }

            // don't regenerate it if it is current
            if (lastRenderEntityUpdate == Game_local.gameLocal.time || !changed) {
                return false
            }
            lastRenderEntityUpdate = Game_local.gameLocal.time
            changed = false
            numTris = 0
            numDecalTris = 0
            i = 0
            while (i < shards.Num()) {
                n = shards.get(i).winding.GetNumPoints()
                if (n > 2) {
                    numTris += n - 2
                }
                k = 0
                while (k < shards.get(i).decals.Num()) {
                    n = shards.get(i).decals.get(k).GetNumPoints()
                    if (n > 2) {
                        numDecalTris += n - 2
                    }
                    k++
                }
                i++
            }

            // FIXME: re-use model surfaces
            renderEntity.hModel.InitEmpty(BrittleFracture.brittleFracture_SnapshotName)

            // allocate triangle surfaces for the fractures and decals
            tris = renderEntity.hModel.AllocSurfaceTriangles(
                numTris * 3,
                if (material.ShouldCreateBackSides()) numTris * 6 else numTris * 3
            )
            decalTris = renderEntity.hModel.AllocSurfaceTriangles(
                numDecalTris * 3,
                if (decalMaterial.ShouldCreateBackSides()) numDecalTris * 6 else numDecalTris * 3
            )
            i = 0
            while (i < shards.Num()) {
                val origin = shards.get(i).clipModel.GetOrigin()
                val axis = shards.get(i).clipModel.GetAxis()
                fade = 1.0f
                if (shards.get(i).droppedTime >= 0) {
                    msec = Game_local.gameLocal.time - shards.get(i).droppedTime - BrittleFracture.SHARD_FADE_START
                    if (msec > 0) {
                        fade =
                            1.0f - msec.toFloat() / (BrittleFracture.SHARD_ALIVE_TIME - BrittleFracture.SHARD_FADE_START)
                    }
                }
                packedColor = Lib.Companion.PackColor(
                    idVec4(
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_RED] * fade,
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_GREEN] * fade,
                        renderEntity.shaderParms[RenderWorld.SHADERPARM_BLUE] * fade,
                        fade
                    )
                ).toInt()
                val winding: idWinding? = shards.get(i).winding
                winding.GetPlane(plane)
                tangents = plane.Normal().times(axis).ToMat3()
                j = 2
                while (j < winding.GetNumPoints()) {
                    v = tris.verts[tris.numVerts++]
                    v.Clear()
                    v.xyz.set(origin.oPlus(winding.get(0).ToVec3().times(axis)))
                    v.st.set(0, winding.get(0).s)
                    v.st.set(1, winding.get(0).t)
                    v.normal.set(tangents.get(0))
                    v.tangents[0] = tangents.get(1)
                    v.tangents[1] = tangents.get(2)
                    v.SetColor(packedColor.toLong())
                    v = tris.verts[tris.numVerts++]
                    v.Clear()
                    v.xyz.set(origin.oPlus(winding.get(j - 1).ToVec3().times(axis)))
                    v.st.set(0, winding.get(j - 1).s)
                    v.st.set(1, winding.get(j - 1).t)
                    v.normal.set(tangents.get(0))
                    v.tangents[0] = tangents.get(1)
                    v.tangents[1] = tangents.get(2)
                    v.SetColor(packedColor.toLong())
                    v = tris.verts[tris.numVerts++]
                    v.Clear()
                    v.xyz.set(origin.oPlus(winding.get(j).ToVec3().times(axis)))
                    v.st.set(0, winding.get(j).s)
                    v.st.set(1, winding.get(j).t)
                    v.normal.set(tangents.get(0))
                    v.tangents[0] = tangents.get(1)
                    v.tangents[1] = tangents.get(2)
                    v.SetColor(packedColor.toLong())
                    tris.indexes[tris.numIndexes++] = tris.numVerts - 3
                    tris.indexes[tris.numIndexes++] = tris.numVerts - 2
                    tris.indexes[tris.numIndexes++] = tris.numVerts - 1
                    if (material.ShouldCreateBackSides()) {
                        tris.indexes[tris.numIndexes++] = tris.numVerts - 2
                        tris.indexes[tris.numIndexes++] = tris.numVerts - 3
                        tris.indexes[tris.numIndexes++] = tris.numVerts - 1
                    }
                    j++
                }
                k = 0
                while (k < shards.get(i).decals.Num()) {
                    val decalWinding: idWinding? = shards.get(i).decals.get(k)
                    j = 2
                    while (j < decalWinding.GetNumPoints()) {
                        v = decalTris.verts[decalTris.numVerts++]
                        v.Clear()
                        v.xyz.set(origin.oPlus(decalWinding.get(0).ToVec3().times(axis)))
                        v.st.set(0, decalWinding.get(0).s)
                        v.st.set(1, decalWinding.get(0).t)
                        v.normal.set(tangents.get(0))
                        v.tangents[0] = tangents.get(1)
                        v.tangents[1] = tangents.get(2)
                        v.SetColor(packedColor.toLong())
                        v = decalTris.verts[decalTris.numVerts++]
                        v.Clear()
                        v.xyz.set(origin.oPlus(decalWinding.get(j - 1).ToVec3().times(axis)))
                        v.st.set(0, decalWinding.get(j - 1).s)
                        v.st.set(1, decalWinding.get(j - 1).t)
                        v.normal.set(tangents.get(0))
                        v.tangents[0] = tangents.get(1)
                        v.tangents[1] = tangents.get(2)
                        v.SetColor(packedColor.toLong())
                        v = decalTris.verts[decalTris.numVerts++]
                        v.Clear()
                        v.xyz.set(origin.oPlus(decalWinding.get(j).ToVec3().times(axis)))
                        v.st.set(0, decalWinding.get(j).s)
                        v.st.set(1, decalWinding.get(j).t)
                        v.normal.set(tangents.get(0))
                        v.tangents[0] = tangents.get(1)
                        v.tangents[1] = tangents.get(2)
                        v.SetColor(packedColor.toLong())
                        decalTris.indexes[decalTris.numIndexes++] = decalTris.numVerts - 3
                        decalTris.indexes[decalTris.numIndexes++] = decalTris.numVerts - 2
                        decalTris.indexes[decalTris.numIndexes++] = decalTris.numVerts - 1
                        if (decalMaterial.ShouldCreateBackSides()) {
                            decalTris.indexes[decalTris.numIndexes++] = decalTris.numVerts - 2
                            decalTris.indexes[decalTris.numIndexes++] = decalTris.numVerts - 3
                            decalTris.indexes[decalTris.numIndexes++] = decalTris.numVerts - 1
                        }
                        j++
                    }
                    k++
                }
                i++
            }
            tris.tangentsCalculated = true
            decalTris.tangentsCalculated = true
            Simd.SIMDProcessor.MinMax(tris.bounds.get(0), tris.bounds.get(1), tris.verts, tris.numVerts)
            Simd.SIMDProcessor.MinMax(
                decalTris.bounds.get(0),
                decalTris.bounds.get(1),
                decalTris.verts,
                decalTris.numVerts
            )

//	memset( &surface, 0, sizeof( surface ) );
            surface = modelSurface_s()
            surface.shader = material
            surface.id = 0
            surface.geometry = tris
            renderEntity.hModel.AddSurface(surface)

//	memset( &surface, 0, sizeof( surface ) );
            surface = modelSurface_s()
            surface.shader = decalMaterial
            surface.id = 1
            surface.geometry = decalTris
            renderEntity.hModel.AddSurface(surface)
            return true
        }

        private fun AddShard(clipModel: idClipModel?, w: idFixedWinding?) {
            val shard = shard_s()
            shard.clipModel = clipModel
            shard.droppedTime = -1
            shard.winding = w
            shard.decals.Clear()
            shard.edgeHasNeighbour.AssureSize(w.GetNumPoints(), false)
            shard.neighbours.Clear()
            shard.atEdge = false
            shards.Append(shard)
        }

        private fun RemoveShard(index: Int) {
            var i: Int

//	delete shards[index];
            shards.set(index, null)
            shards.RemoveIndex(index)
            physicsObj.RemoveIndex(index)
            i = index
            while (i < shards.Num()) {
                shards.get(i).clipModel.SetId(i)
                i++
            }
        }

        private fun DropShard(shard: shard_s?, point: idVec3, dir: idVec3, impulse: Float, time: Int) {
            var i: Int
            var j: Int
            val clipModelId: Int
            val dist: Float
            val f: Float
            val dir2 = idVec3()
            val origin = idVec3()
            val axis: idMat3
            var neighbour: shard_s?

            // don't display decals on dropped shards
            shard.decals.DeleteContents(true)

            // remove neighbour pointers of neighbours pointing to this shard
            i = 0
            while (i < shard.neighbours.Num()) {
                neighbour = shard.neighbours.get(i)
                j = 0
                while (j < neighbour.neighbours.Num()) {
                    if (neighbour.neighbours.get(j) == shard) {
                        neighbour.neighbours.RemoveIndex(j)
                        break
                    }
                    j++
                }
                i++
            }

            // remove neighbour pointers
            shard.neighbours.Clear()

            // remove the clip model from the static physics object
            clipModelId = shard.clipModel.GetId()
            physicsObj.SetClipModel(null, 1.0f, clipModelId, false)
            origin.set(shard.clipModel.GetOrigin())
            axis = shard.clipModel.GetAxis()

            // set the dropped time for fading
            shard.droppedTime = time
            dir2.set(origin.minus(point))
            dist = dir2.Normalize()
            f = if (dist > maxShatterRadius) 1.0f else idMath.Sqrt(dist - minShatterRadius) * (1.0f / idMath.Sqrt(
                maxShatterRadius - minShatterRadius
            ))

            // setup the physics
            shard.physicsObj.SetSelf(this)
            shard.physicsObj.SetClipModel(shard.clipModel, density)
            shard.physicsObj.SetMass(shardMass)
            shard.physicsObj.SetOrigin(origin)
            shard.physicsObj.SetAxis(axis)
            shard.physicsObj.SetBouncyness(bouncyness)
            shard.physicsObj.SetFriction(0.6f, 0.6f, friction)
            shard.physicsObj.SetGravity(Game_local.gameLocal.GetGravity())
            shard.physicsObj.SetContents(Material.CONTENTS_RENDERMODEL)
            shard.physicsObj.SetClipMask(Game_local.MASK_SOLID or Material.CONTENTS_MOVEABLECLIP)
            shard.physicsObj.ApplyImpulse(0, origin, dir.times(impulse * linearVelocityScale))
            shard.physicsObj.SetAngularVelocity(dir.Cross(dir2).times(f * angularVelocityScale))
            shard.clipModel.SetId(clipModelId)
            BecomeActive(Entity.TH_PHYSICS)
        }

        private fun Shatter(point: idVec3, impulse: idVec3, time: Int) {
            var i: Int
            val dir = idVec3()
            var shard: shard_s?
            val m: Float
            if (Game_local.gameLocal.isServer) {
                val msg = idBitMsg()
                val msgBuf = ByteBuffer.allocate(Game_local.MAX_EVENT_PARAM_SIZE)
                msg.Init(msgBuf, Game_local.MAX_EVENT_PARAM_SIZE)
                msg.BeginWriting()
                msg.WriteFloat(point.get(0))
                msg.WriteFloat(point.get(1))
                msg.WriteFloat(point.get(2))
                msg.WriteFloat(impulse.get(0))
                msg.WriteFloat(impulse.get(1))
                msg.WriteFloat(impulse.get(2))
                ServerSendEvent(EVENT_SHATTER, msg, true, -1)
            }
            if (time > Game_local.gameLocal.time - BrittleFracture.SHARD_ALIVE_TIME) {
                StartSound("snd_shatter", gameSoundChannel_t.SND_CHANNEL_ANY, 0, false, null)
            }
            if (!IsBroken()) {
                Break()
            }
            if (fxFracture.Length() != 0) {
                idEntityFx.Companion.StartFx(fxFracture, point, GetPhysics().GetAxis(), this, true)
            }
            dir.set(impulse)
            m = dir.Normalize()
            i = 0
            while (i < shards.Num()) {
                shard = shards.get(i)
                if (shard.droppedTime != -1) {
                    i++
                    continue
                }
                if (shard.clipModel.GetOrigin().minus(point).LengthSqr() > Math_h.Square(maxShatterRadius)) {
                    i++
                    continue
                }
                DropShard(shard, point, dir, m, time)
                i++
            }
            DropFloatingIslands(point, impulse, time)
        }

        private fun DropFloatingIslands(point: idVec3, impulse: idVec3, time: Int) {
            var i: Int
            var j: Int
            var numIslands: Int
            var queueStart: Int
            var queueEnd: Int
            var curShard: shard_s
            var nextShard: shard_s?
            val queue: Array<shard_s?>
            var touchesEdge: Boolean
            val dir = idVec3()
            dir.set(impulse)
            dir.Normalize()
            numIslands = 0
            queue = arrayOfNulls<shard_s?>(shards.Num())
            i = 0
            while (i < shards.Num()) {
                shards.get(i).islandNum = 0
                i++
            }
            i = 0
            while (i < shards.Num()) {
                if (shards.get(i).droppedTime != -1) {
                    i++
                    continue
                }
                if (shards.get(i).islandNum != 0) {
                    i++
                    continue
                }
                queueStart = 0
                queueEnd = 1
                queue[0] = shards.get(i)
                shards.get(i).islandNum = numIslands + 1
                touchesEdge = shards.get(i).atEdge
                curShard = queue[queueStart]
                while (queueStart < queueEnd) {
                    j = 0
                    while (j < curShard.neighbours.Num()) {
                        nextShard = curShard.neighbours.get(j)
                        if (nextShard.droppedTime != -1) {
                            j++
                            continue
                        }
                        if (nextShard.islandNum != 0) {
                            j++
                            continue
                        }
                        queue[queueEnd++] = nextShard
                        nextShard.islandNum = numIslands + 1
                        if (nextShard.atEdge) {
                            touchesEdge = true
                        }
                        j++
                    }
                    curShard = queue[++queueStart]
                }
                numIslands++

                // if the island is not connected to the world at any edges
                if (!touchesEdge) {
                    j = 0
                    while (j < queueEnd) {
                        DropShard(queue[j], point, dir, 0.0f, time)
                        j++
                    }
                }
                i++
            }
        }

        private fun Break() {
            fl.takedamage = false
            physicsObj.SetContents(Material.CONTENTS_RENDERMODEL or Material.CONTENTS_TRIGGER)
        }

        private fun Fracture_r(w: idFixedWinding?) {
            var i: Int
            var j: Int
            var bestPlane: Int
            var a: Float
            var c: Float
            var s: Float
            var dist: Float
            var bestDist: Float
            val origin = idVec3()
            val windingPlane = idPlane()
            val splitPlanes: Array<idPlane> = idPlane.Companion.generateArray(2)
            val axis = idMat3()
            val axistemp = idMat3()
            val back = idFixedWinding()
            val trm = idTraceModel()
            val clipModel: idClipModel
            while (true) {
                origin.set(w.GetCenter())
                w.GetPlane(windingPlane)
                if (w.GetArea() < maxShardArea) {
                    break
                }

                // randomly create a split plane
                a = Game_local.gameLocal.random.RandomFloat() * idMath.TWO_PI
                c = Math.cos(a.toDouble()).toFloat()
                s = -Math.sin(a.toDouble()).toFloat()
                axis.set(2, windingPlane.Normal())
                axis.get(2).NormalVectors(axistemp.get(0), axistemp.get(1))
                axis.set(0, axistemp.get(0).times(c).oPlus(axistemp.get(1).times(s)))
                axis.set(1, axistemp.get(0).times(s).oPlus(axistemp.get(1).times(-c)))

                // get the best split plane
                bestDist = 0.0f
                bestPlane = 0
                i = 0
                while (i < 2) {
                    splitPlanes[i].SetNormal(axis.get(i))
                    splitPlanes[i].FitThroughPoint(origin)
                    j = 0
                    while (j < w.GetNumPoints()) {
                        dist = splitPlanes[i].Distance(w.get(j).ToVec3())
                        if (dist > bestDist) {
                            bestDist = dist
                            bestPlane = i
                        }
                        j++
                    }
                    i++
                }

                // split the winding
                if (0 == w.Split(back, splitPlanes[bestPlane])) {
                    break
                }

                // recursively create shards for the back winding
                Fracture_r(back)
            }

            // translate the winding to it's center
            origin.set(w.GetCenter())
            j = 0
            while (j < w.GetNumPoints()) {
                w.get(j).ToVec3().minusAssign(origin)
                j++
            }
            w.RemoveEqualPoints()
            trm.SetupPolygon(w)
            trm.Shrink(CollisionModel.CM_CLIP_EPSILON)
            clipModel = idClipModel(trm)
            physicsObj.SetClipModel(clipModel, 1.0f, shards.Num())
            physicsObj.SetOrigin(GetPhysics().GetOrigin().oPlus(origin), shards.Num())
            physicsObj.SetAxis(GetPhysics().GetAxis(), shards.Num())
            AddShard(clipModel, w)
        }

        private fun CreateFractures(renderModel: idRenderModel?) {
            var i: Int
            var j: Int
            var k: Int
            var surf: modelSurface_s?
            var v: idDrawVert?
            val w = idFixedWinding()
            if (TempDump.NOT(renderModel)) {
                return
            }
            physicsObj.SetSelf(this)
            physicsObj.SetOrigin(GetPhysics().GetOrigin(), 0)
            physicsObj.SetAxis(GetPhysics().GetAxis(), 0)
            i = 0
            while (i < 1 /*renderModel.NumSurfaces()*/) {
                surf = renderModel.Surface(i)
                material = surf.shader
                j = 0
                while (j < surf.geometry.numIndexes) {
                    w.Clear()
                    k = 0
                    while (k < 3) {
                        v = surf.geometry.verts[surf.geometry.indexes[j + 2 - k]]
                        w.AddPoint(v.xyz)
                        w.get(k).s = v.st.get(0)
                        w.get(k).t = v.st.get(1)
                        k++
                    }
                    Fracture_r(w)
                    j += 3
                }
                i++
            }
            physicsObj.SetContents(material.GetContentFlags())
            SetPhysics(physicsObj)
        }

        private fun FindNeighbours() {
            var i: Int
            var j: Int
            var k: Int
            var l: Int
            val p1 = idVec3()
            val p2 = idVec3()
            val dir = idVec3()
            var axis: idMat3
            val plane: Array<idPlane> = idPlane.Companion.generateArray(4)
            i = 0
            while (i < shards.Num()) {
                val shard1 = shards.get(i)
                val w1: idWinding? = shard1.winding
                val origin1 = shard1.clipModel.GetOrigin()
                val axis1 = shard1.clipModel.GetAxis()
                k = 0
                while (k < w1.GetNumPoints()) {
                    p1.set(origin1.oPlus(w1.get(k).ToVec3().times(axis1)))
                    p2.set(origin1.oPlus(w1.get((k + 1) % w1.GetNumPoints()).ToVec3().times(axis1)))
                    dir.set(p2.minus(p1))
                    dir.Normalize()
                    axis = dir.ToMat3()
                    plane[0].SetNormal(dir)
                    plane[0].FitThroughPoint(p1)
                    plane[1].SetNormal(dir.oNegative())
                    plane[1].FitThroughPoint(p2)
                    plane[2].SetNormal(axis.get(1))
                    plane[2].FitThroughPoint(p1)
                    plane[3].SetNormal(axis.get(2))
                    plane[3].FitThroughPoint(p1)
                    j = 0
                    while (j < shards.Num()) {
                        if (i == j) {
                            j++
                            continue
                        }
                        val shard2 = shards.get(j)
                        l = 0
                        while (l < shard1.neighbours.Num()) {
                            if (shard1.neighbours.get(l) == shard2) {
                                break
                            }
                            l++
                        }
                        if (l < shard1.neighbours.Num()) {
                            j++
                            continue
                        }
                        val w2: idWinding? = shard2.winding
                        val origin2 = shard2.clipModel.GetOrigin()
                        val axis2 = shard2.clipModel.GetAxis()
                        l = w2.GetNumPoints() - 1
                        while (l >= 0) {
                            p1.set(origin2.oPlus(w2.get(l).ToVec3().times(axis2)))
                            p2.set(
                                origin2.oPlus(
                                    w2.get((l - 1 + w2.GetNumPoints()) % w2.GetNumPoints()).ToVec3().times(axis2)
                                )
                            )
                            if (plane[0].Side(p2, 0.1f) == Plane.SIDE_FRONT && plane[1].Side(
                                    p1,
                                    0.1f
                                ) == Plane.SIDE_FRONT
                            ) {
                                if (plane[2].Side(p1, 0.1f) == Plane.SIDE_ON && plane[3].Side(
                                        p1,
                                        0.1f
                                    ) == Plane.SIDE_ON
                                ) {
                                    if (plane[2].Side(p2, 0.1f) == Plane.SIDE_ON && plane[3].Side(
                                            p2,
                                            0.1f
                                        ) == Plane.SIDE_ON
                                    ) {
                                        shard1.neighbours.Append(shard2)
                                        shard1.edgeHasNeighbour.set(k, true)
                                        shard2.neighbours.Append(shard1)
                                        shard2.edgeHasNeighbour.set(
                                            (l - 1 + w2.GetNumPoints()) % w2.GetNumPoints(),
                                            true
                                        )
                                        break
                                    }
                                }
                            }
                            l--
                        }
                        j++
                    }
                    k++
                }
                k = 0
                while (k < w1.GetNumPoints()) {
                    if (!shard1.edgeHasNeighbour.get(k)) {
                        break
                    }
                    k++
                }
                shard1.atEdge = k < w1.GetNumPoints()
                i++
            }
        }

        private fun Event_Activate(activator: idEventArg<idEntity?>?) {
            disableFracture = false
            if (health <= 0) {
                Break()
            }
        }

        private fun Event_Touch(_other: idEventArg<idEntity?>?, _trace: idEventArg<trace_s?>?) {
            val other = _other.value
            val trace = _trace.value
            val point = idVec3()
            val impulse = idVec3()
            if (!IsBroken()) {
                return
            }
            if (trace.c.id < 0 || trace.c.id >= shards.Num()) {
                return
            }
            point.set(shards.get(trace.c.id).clipModel.GetOrigin())
            impulse.set(other.GetPhysics().GetLinearVelocity().times(other.GetPhysics().GetMass()))
            Shatter(point, impulse, Game_local.gameLocal.time)
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*>? {
            return eventCallbacks.get(event)
        }

        // virtual						~idBrittleFracture( void );
        override fun _deconstructor() {
            var i: Int
            i = 0
            while (i < shards.Num()) {
                shards.get(i).decals.DeleteContents(true)
                i++
            }

            // make sure the render entity is freed before the model is freed
            FreeModelDef()
            ModelManager.renderModelManager.FreeModel(renderEntity.hModel)
            super._deconstructor()
        }

        class ModelCallback private constructor() : deferredEntityCallback_t() {
            override fun run(e: renderEntity_s, v: renderView_s?): Boolean {
                val ent: idBrittleFracture
                ent = Game_local.gameLocal.entities[e.entityNum]
                if (null == ent) {
                    idGameLocal.Companion.Error("idBrittleFracture::ModelCallback: callback with NULL game entity")
                }
                return ent.UpdateRenderEntity(e, v)
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
                val instance: deferredEntityCallback_t? = BrittleFracture.idBrittleFracture.ModelCallback()
                fun getInstance(): deferredEntityCallback_t? {
                    return BrittleFracture.idBrittleFracture.ModelCallback.Companion.instance
                }
            }
        }

        //
        //
        init {
            physicsObj = idPhysics_StaticMulti()
            material = null
            decalMaterial = null
            decalSize = 0f
            maxShardArea = 0f
            maxShatterRadius = 0f
            minShatterRadius = 0f
            linearVelocityScale = 0f
            angularVelocityScale = 0f
            shardMass = 0f
            density = 0f
            friction = 0f
            bouncyness = 0f
            fxFracture = idStr()
            shards = idList()
            bounds = idBounds.Companion.ClearBounds()
            disableFracture = false
            lastRenderEntityUpdate = -1
            changed = false
            fl.networkSync = true
        }
    }
}