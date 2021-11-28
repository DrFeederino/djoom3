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
        point.oMinSet(origin)
        proj.oSet(axis.oMultiply(point.oMultiply(axis)))
        v1.oSet(point.oMinus(proj))
        v2.oSet(axis.Cross(v1))
        val t: Double = (tanHalfAngle * tanHalfAngle).toDouble()
        val d: Double = 1.0f / (1.0f + t)
        val s: Double = 2.0f * tanHalfAngle * d
        val c: Double = (1.0f - t) * d
        point.oSet(v1.oMultiply(c.toFloat()).oMinus(v2.oMultiply(s.toFloat())).oPlus(proj.oPlus(origin)))
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
        t = (tanHalfAngle.getVal() * tanHalfAngle.getVal()).toDouble()
        d = 1.0f / (1.0f + t)
        s = 2.0f * tanHalfAngle.getVal() * d
        c = (1.0f - t) * d
        start.oMinSet(origin)
        proj.oSet(axis.oMultiply(start.oMultiply(axis)))
        v1.oSet(start.oMinus(proj))
        v2.oSet(axis.Cross(v1))
        start.oSet(v1.oMultiply(c.toFloat()).oMinus(v2.oMultiply(s.toFloat())).oPlus(proj.oPlus(origin)))
        end.oMinSet(origin)
        proj.oSet(axis.oMultiply(end.oMultiply(axis)))
        v1.oSet(end.oMinus(proj))
        v2.oSet(axis.Cross(v1))
        end.oSet(v1.oMultiply(c.toFloat()).oMinus(v2.oMultiply(s.toFloat())).oPlus(proj.oPlus(origin)))
    }
}