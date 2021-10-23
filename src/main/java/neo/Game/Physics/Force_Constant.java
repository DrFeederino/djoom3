package neo.Game.Physics;

import neo.Game.GameSys.SaveGame.idRestoreGame;
import neo.Game.GameSys.SaveGame.idSaveGame;
import neo.Game.Physics.Force.idForce;
import neo.Game.Physics.Physics.idPhysics;
import neo.idlib.math.Vector.idVec3;

import static neo.idlib.math.Vector.getVec3_zero;

/**
 *
 */
public class Force_Constant {

    /*
     ===============================================================================

     Constant force

     ===============================================================================
     */
    public static class idForce_Constant extends idForce {
        // CLASS_PROTOTYPE( idForce_Constant );

        // force properties
        private final idVec3 force;
        private int id;
        private idPhysics physics;
        private final idVec3 point;
        //
        //

        public idForce_Constant() {
            force = getVec3_zero();
            physics = null;
            id = 0;
            point = getVec3_zero();
        }
        // virtual				~idForce_Constant( void );

        @Override
        public void Save(idSaveGame savefile) {
            savefile.WriteVec3(force);
            savefile.WriteInt(id);
            savefile.WriteVec3(point);
        }

        @Override
        public void Restore(idRestoreGame savefile) {
            // Owner needs to call SetPhysics!!
            savefile.ReadVec3(force);
            id = savefile.ReadInt();
            savefile.ReadVec3(point);
        }

        // constant force
        public void SetForce(final idVec3 force) {
            this.force.oSet(force);
        }
        // set force position

        public void SetPosition(idPhysics physics, int id, final idVec3 point) {
            this.physics = physics;
            this.id = id;
            this.point.oSet(point);
        }

        public void SetPhysics(idPhysics physics) {
            this.physics = physics;
        }

        // common force interface
        @Override
        public void Evaluate(int time) {
            final idVec3 p = new idVec3();

            if (null == physics) {
                return;
            }

            p.oSet(physics.GetOrigin(id).oPlus(point.oMultiply(physics.GetAxis(id))));

            physics.AddForce(id, p, force);
        }

        @Override
        public void RemovePhysics(final idPhysics phys) {
            if (physics == phys) {
                physics = null;
            }
        }
    }

}
