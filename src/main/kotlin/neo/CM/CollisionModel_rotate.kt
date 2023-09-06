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
        proj.set(axis * (point * axis))
        v1.set(point - proj)
        v2.set(axis.Cross(v1))

        val t: Float = tanHalfAngle * tanHalfAngle
        val d: Float = 1.0f / (1.0f + t)
        val s: Float = 2.0f * tanHalfAngle * d
        val c: Float = (1.0f - t) * d

        point.set(v1 * c - v2 * s + proj + origin)
    }

    /*
     ================
     CM_RotateEdge

     rotates an edge about an arbitrary axis using the tangent of half the rotation angle
     ================
     */
    fun CM_RotateEdge(start: idVec3, end: idVec3, origin: idVec3, axis: idVec3, tanHalfAngle: CFloat) {
        val d: Float
        val t: Float
        val s: Float
        val c: Float
        val proj = idVec3()
        val v1 = idVec3()
        val v2 = idVec3()

        t = (tanHalfAngle._val * tanHalfAngle._val)
        d = 1.0f / (1.0f + t)
        s = 2.0f * tanHalfAngle._val * d
        c = (1.0f - t) * d

        start.minusAssign(origin)
        proj.set(axis * (start * axis))
        v1.set(start - proj)
        v2.set(axis.Cross(v1))
        start.set(v1 * c - v2 * s + proj + origin)

        end.minusAssign(origin)
        proj.set(axis * (end * axis))
        v1.set(end - proj)
        v2.set(axis.Cross(v1))
        end.set(v1 * c - v2 * s + proj + origin)
    }
}