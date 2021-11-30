package neo.Game.Physics

import neo.Game.GameSys.SaveGame.idRestoreGame
import neo.Game.GameSys.SaveGame.idSaveGame
import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
class Force_Constant {
    /*
     ===============================================================================

     Constant force

     ===============================================================================
     */
    class idForce_Constant : idForce() {
        // CLASS_PROTOTYPE( idForce_Constant );
        // force properties
        private val force: idVec3?
        private var id: Int
        private var physics: idPhysics?
        private val point: idVec3?

        // virtual				~idForce_Constant( void );
        override fun Save(savefile: idSaveGame?) {
            savefile.WriteVec3(force)
            savefile.WriteInt(id)
            savefile.WriteVec3(point)
        }

        override fun Restore(savefile: idRestoreGame?) {
            // Owner needs to call SetPhysics!!
            savefile.ReadVec3(force)
            id = savefile.ReadInt()
            savefile.ReadVec3(point)
        }

        // constant force
        fun SetForce(force: idVec3?) {
            this.force.set(force)
        }

        // set force position
        fun SetPosition(physics: idPhysics?, id: Int, point: idVec3?) {
            this.physics = physics
            this.id = id
            this.point.set(point)
        }

        fun SetPhysics(physics: idPhysics?) {
            this.physics = physics
        }

        // common force interface
        override fun Evaluate(time: Int) {
            val p = idVec3()
            if (null == physics) {
                return
            }
            p.set(physics.GetOrigin(id).oPlus(point.times(physics.GetAxis(id))))
            physics.AddForce(id, p, force)
        }

        override fun RemovePhysics(phys: idPhysics?) {
            if (physics === phys) {
                physics = null
            }
        }

        //
        //
        init {
            force = Vector.getVec3_zero()
            physics = null
            id = 0
            point = Vector.getVec3_zero()
        }
    }
}