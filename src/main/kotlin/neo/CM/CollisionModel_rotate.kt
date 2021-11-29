package neo.CM

import neo.idlib.containers.CFloat
import neo.idlib.math.Vector.idVec3

/**
 *
 */
object CollisionModel_rotate {
    /*
     ===============================================================================

     Trace model vs. polygonal model collision detection.

     ===============================================================================
     */
    /*
     ===============================================================================

     Collision detection for rotational motion

     ===============================================================================
     */
    // epsilon for round-off errors in epsilon calculations
    const val CM_PL_RANGE_EPSILON = 1e-4f

    // if the collision point is this close to the rotation axis it is not considered a collision
    const val ROTATION_AXIS_EPSILON = CollisionModel.CM_CLIP_EPSILON * 0.25f

    /*
     ================
     CM_RotatePoint

     rotates a point about an arbitrary axis using the tangent of half the rotation angle
     ================
     */
    fun CM_RotatePoint(point: idVec3, origin: idVec3, axis: idVec3, tanHalfAngle: Float) {
        val proj = idVec3()
        val v1 = idVec3()
        val v2 = idVec3()
        point.minusAssign(origin)
        proj.oSet(axis.times(point.times(axis)))
        v1.oSet(point - proj)
        v2.oSet(axis.Cross(v1))
        val t: Double = (tanHalfAngle * tanHalfAngle).toDouble()
        val d: Double = 1.0f / (1.0f + t)
        val s: Double = 2.0f * tanHalfAngle * d
        val c: Double = (1.0f - t) * d
        point.oSet(v1.times(c.toFloat()).minus(v2.times(s.toFloat())).plus(proj.plus(origin)))
    }

    /*
     ================
     CM_RotateEdge

     rotates an edge about an arbitrary axis using the tangent of half the rotation angle
     ================
     */
    fun CM_RotateEdge(start: idVec3, end: idVec3, origin: idVec3, axis: idVec3, tanHalfAngle: CFloat) {
        val d: Double
        val t: Double
        val s: Double
        val c: Double
        val proj = idVec3()
        val v1 = idVec3()
        val v2 = idVec3()
        t = (tanHalfAngle._val * tanHalfAngle._val).toDouble()
        d = 1.0f / (1.0f + t)
        s = 2.0f * tanHalfAngle._val * d
        c = (1.0f - t) * d
        start.minusAssign(origin)
        proj.oSet(axis.times(start.times(axis)))
        v1.oSet(start.minus(proj))
        v2.oSet(axis.Cross(v1))
        start.oSet(v1.times(c.toFloat()).minus(v2.times(s.toFloat())).plus(proj.plus(origin)))
        end.minusAssign(origin)
        proj.oSet(axis.times(end.times(axis)))
        v1.oSet(end.minus(proj))
        v2.oSet(axis.Cross(v1))
        end.oSet(v1.times(c.toFloat()).minus(v2.times(s.toFloat())).plus(proj.plus(origin)))
    }
}