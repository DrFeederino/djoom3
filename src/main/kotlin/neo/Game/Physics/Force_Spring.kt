package neo.Game.Physics

import neo.Game.Physics.Force.idForce
import neo.Game.Physics.Physics.idPhysics
import neo.Game.Physics.Physics.impactInfo_s
import neo.idlib.math.Math_h
import neo.idlib.math.Matrix.idMat3
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3

/**
 *
 */
class Force_Spring {
    /*
     ===============================================================================

     Spring force

     ===============================================================================
     */
    class idForce_Spring : idForce() {
        //	CLASS_PROTOTYPE( idForce_Spring );
        private var Kcompress = 100.0f

        // spring properties
        private var Kstretch = 100.0f
        private var damping = 0.0f
        private var id1 // clip model id of first physics object
                = 0
        private var id2 // clip model id of second physics object
                : Int
        private val p1 // position on clip model
                : idVec3?
        private val p2 // position on clip model
                : idVec3?

        //
        // positioning
        private var physics1 // first physics object
                : idPhysics? = null
        private var physics2 // second physics object
                : idPhysics?
        private var restLength = 0.0f

        //	virtual				~idForce_Spring( void );
        // initialize the spring
        fun InitSpring(Kstretch: Float, Kcompress: Float, damping: Float, restLength: Float) {
            this.Kstretch = Kstretch
            this.Kcompress = Kcompress
            this.damping = damping
            this.restLength = restLength
        }

        // set the entities and positions on these entities the spring is attached to
        fun SetPosition(physics1: idPhysics?, id1: Int, p1: idVec3?, physics2: idPhysics?, id2: Int, p2: idVec3?) {
            this.physics1 = physics1
            this.id1 = id1
            this.p1.oSet(p1)
            this.physics2 = physics2
            this.id2 = id2
            this.p2.oSet(p2)
        }

        // common force interface
        override fun Evaluate(time: Int) {
            val length: Float
            var axis: idMat3?
            val pos1 = idVec3()
            val pos2 = idVec3()
            val velocity1 = idVec3()
            val velocity2 = idVec3()
            val force = idVec3()
            val dampingForce = idVec3()
            var info: impactInfo_s? = impactInfo_s()
            pos1.oSet(p1)
            pos2.oSet(p2)
            velocity2.oSet(Vector.getVec3_origin())
            velocity1.oSet(Vector.getVec3_origin())
            if (physics1 != null) {
                axis = physics1.GetAxis(id1)
                pos1.oSet(physics1.GetOrigin(id1))
                pos1.oPluSet(p1.oMultiply(axis))
                if (damping > 0.0f) {
                    info = physics1.GetImpactInfo(id1, pos1)
                    velocity1.oSet(info.velocity)
                }
            }
            if (physics2 != null) {
                axis = physics2.GetAxis(id2)
                pos2.oSet(physics2.GetOrigin(id2))
                pos2.oPluSet(p2.oMultiply(axis))
                if (damping > 0.0f) {
                    info = physics2.GetImpactInfo(id2, pos2)
                    velocity2.oSet(info.velocity)
                }
            }
            force.oSet(pos2.oMinus(pos1))
            dampingForce.oSet(
                force.oMultiply(
                    damping * (velocity2.oMinus(velocity1).oMultiply(force) / force.oMultiply(
                        force
                    ))
                )
            )
            length = force.Normalize()

            // if the spring is stretched
            if (length > restLength) {
                if (Kstretch > 0.0f) {
                    force.oSet(force.oMultiply(Math_h.Square(length - restLength) * Kstretch).oMinus(dampingForce))
                    if (physics1 != null) {
                        physics1.AddForce(id1, pos1, force)
                    }
                    if (physics2 != null) {
                        physics2.AddForce(id2, pos2, force.oNegative())
                    }
                }
            } else {
                if (Kcompress > 0.0f) {
                    force.oSet(force.oMultiply(Math_h.Square(length - restLength) * Kcompress).oMinSet(dampingForce))
                    if (physics1 != null) {
                        physics1.AddForce(id1, pos1, force.oNegative())
                    }
                    if (physics2 != null) {
                        physics2.AddForce(id2, pos2, force)
                    }
                }
            }
        }

        override fun RemovePhysics(phys: idPhysics?) {
            if (physics1 == phys) {
                physics1 = null
            }
            if (physics2 == phys) {
                physics2 = null
            }
        }

        //
        //
        init {
            p1 = Vector.getVec3_zero()
            physics2 = null
            id2 = 0
            p2 = Vector.getVec3_zero()
        }
    }
}