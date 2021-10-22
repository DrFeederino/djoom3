package neo.CM;

import neo.idlib.containers.CFloat;
import neo.idlib.math.Vector.idVec3;

import static neo.CM.CollisionModel.CM_CLIP_EPSILON;

/**
 *
 */
public class CollisionModel_rotate {

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
    static final float CM_PL_RANGE_EPSILON = 1e-4f;
    // if the collision point is this close to the rotation axis it is not considered a collision
    static final float ROTATION_AXIS_EPSILON = (CM_CLIP_EPSILON * 0.25f);


    /*
     ================
     CM_RotatePoint

     rotates a point about an arbitrary axis using the tangent of half the rotation angle
     ================
     */
    static void CM_RotatePoint(final idVec3 point, final idVec3 origin, final idVec3 axis, final float tanHalfAngle) {
        double d, t, s, c;
        final idVec3 proj = new idVec3(), v1 = new idVec3(), v2 = new idVec3();

        point.oMinSet(origin);
        proj.oSet(axis.oMultiply(point.oMultiply(axis)));
        v1.oSet(point.oMinus(proj));
        v2.oSet(axis.Cross(v1));

        t = tanHalfAngle * tanHalfAngle;
        d = 1.0f / (1.0f + t);
        s = 2.0f * tanHalfAngle * d;
        c = (1.0f - t) * d;

        point.oSet(v1.oMultiply((float) c).oMinus(v2.oMultiply((float) s)).oPlus(proj.oPlus(origin)));
    }

    /*
     ================
     CM_RotateEdge

     rotates an edge about an arbitrary axis using the tangent of half the rotation angle
     ================
     */
    static void CM_RotateEdge(final idVec3 start, final idVec3 end, final idVec3 origin, final idVec3 axis, CFloat tanHalfAngle) {
        double d, t, s, c;
        final idVec3 proj = new idVec3(), v1 = new idVec3(), v2 = new idVec3();

        t = tanHalfAngle.getVal() * tanHalfAngle.getVal();
        d = 1.0f / (1.0f + t);
        s = 2.0f * tanHalfAngle.getVal() * d;
        c = (1.0f - t) * d;

        start.oMinSet(origin);
        proj.oSet(axis.oMultiply(start.oMultiply(axis)));
        v1.oSet(start.oMinus(proj));
        v2.oSet(axis.Cross(v1));
        start.oSet(v1.oMultiply((float) c).oMinus(v2.oMultiply((float) s)).oPlus(proj.oPlus(origin)));

        end.oMinSet(origin);
        proj.oSet(axis.oMultiply(end.oMultiply(axis)));
        v1.oSet(end.oMinus(proj));
        v2.oSet(axis.Cross(v1));
        end.oSet(v1.oMultiply((float) c).oMinus(v2.oMultiply((float) s)).oPlus(proj.oPlus(origin)));
    }

}
