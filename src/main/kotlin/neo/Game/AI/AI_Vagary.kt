package neo.Game.AI

import neo.Game.AI.AI.idAI
import neo.Game.Entity.idEntity
import neo.Game.GameSys.Class.eventCallback_t
import neo.Game.GameSys.Class.eventCallback_t2
import neo.Game.GameSys.Class.eventCallback_t5
import neo.Game.GameSys.Class.idEventArg
import neo.Game.GameSys.Event.idEventDef
import neo.Game.GameSys.SysCvar
import neo.Game.Game_local
import neo.Game.Moveable.idMoveable
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Script.Script_Thread.idThread
import neo.idlib.BV.Bounds.idBounds
import neo.idlib.Lib
import neo.idlib.math.Vector.idVec3

/**
 *
 */
class AI_Vagary {
    companion object {
        /* **********************************************************************

         game/ai/AI_Vagary.cpp

         Vagary specific AI code

         ***********************************************************************/
        private val AI_Vagary_ChooseObjectToThrow: idEventDef = idEventDef("vagary_ChooseObjectToThrow", "vvfff", 'e')
        private val AI_Vagary_ThrowObjectAtEnemy: idEventDef = idEventDef("vagary_ThrowObjectAtEnemy", "ef")
    }

    class idAI_Vagary : idAI() {
        companion object {
            //CLASS_PROTOTYPE( idAI_Vagary );
            private val eventCallbacks: MutableMap<idEventDef, eventCallback_t<*>> = HashMap()
            fun getEventCallBacks(): MutableMap<idEventDef, eventCallback_t<*>> {
                return eventCallbacks
            }

            init {
                eventCallbacks.putAll(idAI.getEventCallBacks())
                eventCallbacks[AI_Vagary_ChooseObjectToThrow] = object : eventCallback_t5<idAI_Vagary> { println(it) }
                eventCallbacks[AI_Vagary_ChooseObjectToThrow] =
                    eventCallback_t5<idAI_Vagary> { obj: Any?, mins: idEventArg<*>? ->
                        idAI_Vagary::Event_ChooseObjectToThrow
                    } as eventCallback_t5<idAI_Vagary>
                eventCallbacks[AI_Vagary_ThrowObjectAtEnemy] =
                    eventCallback_t2<idAI_Vagary> { obj: Any?, _ent: idEventArg<*>? ->
                        idAI_Vagary::Event_ThrowObjectAtEnemy
                    } as eventCallback_t2<idAI_Vagary>
            }
        }

        private fun Event_ChooseObjectToThrow(
            mins: idEventArg<idVec3>, maxs: idEventArg<idVec3>,
            speed: idEventArg<Float>, minDist: idEventArg<Float>, offset: idEventArg<Float>
        ) {
            var ent: idEntity
            val entityList = Array(Game_local.MAX_GENTITIES) { idEntity() }
            val numListedEntities: Int
            var i: Int
            var index: Int
            var dist: Float
            val vel = idVec3()
            val offsetVec = idVec3(0f, 0f, offset.value!!)
            val enemyEnt: idEntity? = enemy.GetEntity()
            if (null == enemyEnt) {
                idThread.ReturnEntity(null)
            }
            val enemyEyePos = lastVisibleEnemyPos + lastVisibleEnemyEyeOffset
            val myBounds = physicsObj.GetAbsBounds()
            val checkBounds = idBounds(mins.value!!, maxs.value!!)
            checkBounds.TranslateSelf(physicsObj.GetOrigin())
            numListedEntities =
                Game_local.gameLocal.clip.EntitiesTouchingBounds(checkBounds, -1, entityList, Game_local.MAX_GENTITIES)
            index = Game_local.gameLocal.random.RandomInt(numListedEntities.toDouble())
            i = 0
            while (i < numListedEntities) {
                if (index >= numListedEntities) {
                    index = 0
                }
                ent = entityList[index]
                if (ent !is idMoveable) {
                    i++
                    index++
                    continue
                }
                if (ent.fl.hidden) {
                    // don't throw hidden objects
                    i++
                    index++
                    continue
                }
                val entPhys = ent.GetPhysics()
                val entOrg = entPhys.GetOrigin()
                dist = entOrg.minus(enemyEyePos).LengthFast()
                if (dist < minDist.value!!) {
                    i++
                    index++
                    continue
                }
                val expandedBounds = myBounds.Expand(entPhys.GetBounds().GetRadius())
                if (expandedBounds.LineIntersection(entOrg, enemyEyePos)) {
                    // ignore objects that are behind us
                    i++
                    index++
                    continue
                }
                if (PredictTrajectory(
                        entPhys.GetOrigin() + offsetVec,
                        enemyEyePos,
                        speed.value!!,
                        entPhys.GetGravity(),
                        entPhys.GetClipModel(),
                        entPhys.GetClipMask(),
                        Lib.MAX_WORLD_SIZE.toFloat(),
                        null,
                        enemyEnt,
                        if (SysCvar.ai_debugTrajectory.GetBool()) 4000 else 0,
                        vel
                    )
                ) {
                    idThread.ReturnEntity(ent)
                    return
                }
                i++
                index++
            }
            idThread.ReturnEntity(null)
        }

        private fun Event_ThrowObjectAtEnemy(_ent: idEventArg<idEntity>, _speed: idEventArg<Float>) {
            val ent = _ent.value!!
            val speed: Float = _speed.value!!
            val vel = idVec3()
            val enemyEnt: idEntity?
            val entPhys: idPhysics?
            entPhys = ent.GetPhysics()
            enemyEnt = enemy.GetEntity()
            if (null == enemyEnt) {
                vel.set((viewAxis[0] * physicsObj.GetGravityAxis()) * speed)
            } else {
                PredictTrajectory(
                    entPhys.GetOrigin(),
                    lastVisibleEnemyPos + lastVisibleEnemyEyeOffset,
                    speed,
                    entPhys.GetGravity(),
                    entPhys.GetClipModel(),
                    entPhys.GetClipMask(),
                    Lib.MAX_WORLD_SIZE.toFloat(),
                    null,
                    enemyEnt,
                    if (SysCvar.ai_debugTrajectory.GetBool()) 4000 else 0,
                    vel
                )
                vel.timesAssign(speed)
            }
            entPhys.SetLinearVelocity(vel)
            if (ent is idMoveable) {
                val ment = ent
                ment.EnableDamage(true, 2.5f)
            }
        }

        override fun getEventCallBack(event: idEventDef): eventCallback_t<*> {
            return eventCallbacks[event]!!
        }

    }
}