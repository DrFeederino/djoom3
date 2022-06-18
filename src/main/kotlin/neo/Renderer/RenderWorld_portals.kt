package neo.Renderer

import neo.Renderer.RenderWorld_local.portal_s
import neo.Renderer.tr_local.idScreenRect
import neo.idlib.math.Plane.idPlane

/**
 *
 */
object RenderWorld_portals {
    /*


     All that is done in these functions is the creation of viewLights
     and viewEntitys for the lightDefs and entityDefs that are visible
     in the portal areas that can be seen from the current viewpoint.

     */
    //
    //
    // if we hit this many planes, we will just stop cropping the
    // view down, which is still correct, just conservative
    const val MAX_PORTAL_PLANES = 20

    class portalStack_s {
        var next: portalStack_s? = null

        //
        var numPortalPlanes = 0
        var p: portal_s?
        val portalPlanes: Array<idPlane> = idPlane.generateArray(MAX_PORTAL_PLANES + 1)

        //
        var rect: idScreenRect

        // positive side is outside the visible frustum
        constructor() {
            p = portal_s()
            rect = idScreenRect()
        }

        constructor(p: portalStack_s) {
            this.p = p.p
            next = p.next
            rect = idScreenRect(p.rect)
            for (i in portalPlanes.indices) {
                portalPlanes[i].set(p.portalPlanes[i])
            }
        }
    }
}