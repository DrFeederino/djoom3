package neo.Renderer

import neo.Renderer.Model.srfTriangles_s
import neo.Renderer.tr_local.drawSurf_s
import neo.framework.CmdSystem.cmdFunction_t
import neo.framework.Common
import neo.idlib.CmdArgs
import neo.idlib.Text.Str.idStr
import neo.idlib.geometry.DrawVert.idDrawVert
import neo.idlib.math.Plane.idPlane
import neo.idlib.math.Vector
import neo.idlib.math.Vector.idVec3
import neo.ui.UserInterface
import neo.ui.UserInterface.idUserInterface

/**
 *
 */
object tr_guisurf {
    /*
     ==========================================================================================

     GUI SHADERS

     ==========================================================================================
     */
    /*
     ================
     R_SurfaceToTextureAxis

     Calculates two axis for the surface sutch that a point dotted against
     the axis will give a 0.0 to 1.0 range in S and T when inside the gui surface
     ================
     */
    fun R_SurfaceToTextureAxis(tri: srfTriangles_s?, origin: idVec3?, axis: Array<idVec3?>? /*[3]*/) {
        val area: Float
        val inva: Float
        val d0 = FloatArray(5)
        val d1 = FloatArray(5)
        val a: idDrawVert?
        val b: idDrawVert?
        val c: idDrawVert?
        val bounds = Array<FloatArray?>(2) { FloatArray(2) }
        val boundsOrg = FloatArray(2)
        var i: Int
        var j: Int
        var v: Float

        // find the bounds of the texture
        bounds[0].get(1) = 999999
        bounds[0].get(0) = bounds[0].get(1)
        bounds[1].get(1) = -999999
        bounds[1].get(0) = bounds[1].get(1)
        i = 0
        while (i < tri.numVerts) {
            j = 0
            while (j < 2) {
                v = tri.verts[i].st.get(j)
                if (v < bounds[0].get(j)) {
                    bounds[0].get(j) = v
                }
                if (v > bounds[1].get(j)) {
                    bounds[1].get(j) = v
                }
                j++
            }
            i++
        }

        // use the floor of the midpoint as the origin of the
        // surface, which will prevent a slight misalignment
        // from throwing it an entire cycle off
        boundsOrg[0] = Math.floor((bounds[0].get(0) + bounds[1].get(0)) * 0.5).toFloat()
        boundsOrg[1] = Math.floor((bounds[0].get(1) + bounds[1].get(1)) * 0.5).toFloat()

        // determine the world S and T vectors from the first drawSurf triangle
        a = tri.verts[tri.indexes[0]]
        b = tri.verts[tri.indexes[1]]
        c = tri.verts[tri.indexes[2]]
        Vector.VectorSubtract(b.xyz, a.xyz, d0)
        d0[3] = b.st.get(0) - a.st.get(0)
        d0[4] = b.st.get(1) - a.st.get(1)
        Vector.VectorSubtract(c.xyz, a.xyz, d1)
        d1[3] = c.st.get(0) - a.st.get(0)
        d1[4] = c.st.get(1) - a.st.get(1)
        area = d0[3] * d1[4] - d0[4] * d1[3]
        if (area.toDouble() == 0.0) {
            axis.get(0).Zero()
            axis.get(1).Zero()
            axis.get(2).Zero()
            return  // degenerate
        }
        inva = 1.0f / area
        axis.get(0).set(0, (d0[0] * d1[4] - d0[4] * d1[0]) * inva)
        axis.get(0).set(1, (d0[1] * d1[4] - d0[4] * d1[1]) * inva)
        axis.get(0).set(2, (d0[2] * d1[4] - d0[4] * d1[2]) * inva)
        axis.get(1).set(0, (d0[3] * d1[0] - d0[0] * d1[3]) * inva)
        axis.get(1).set(1, (d0[3] * d1[1] - d0[1] * d1[3]) * inva)
        axis.get(1).set(2, (d0[3] * d1[2] - d0[2] * d1[3]) * inva)
        val plane = idPlane()
        plane.FromPoints(a.xyz, b.xyz, c.xyz)
        axis.get(2).set(0, plane.get(0))
        axis.get(2).set(1, plane.get(1))
        axis.get(2).set(2, plane.get(2))

        // take point 0 and project the vectors to the texture origin
        Vector.VectorMA(a.xyz, boundsOrg[0] - a.st.get(0), axis.get(0), origin)
        Vector.VectorMA(origin, boundsOrg[1] - a.st.get(1), axis.get(1), origin)
    }

    /*
     =================
     R_RenderGuiSurf

     Create a texture space on the given surface and
     call the GUI generator to create quads for it.
     =================
     */
    fun R_RenderGuiSurf(gui: idUserInterface?, drawSurf: drawSurf_s?) {
        val origin = idVec3()
        val axis: Array<idVec3?> = idVec3.Companion.generateArray(3)

        // for testing the performance hit
        if (RenderSystem_init.r_skipGuiShaders.GetInteger() == 1) {
            return
        }

        // don't allow an infinite recursion loop
        if (tr_local.tr.guiRecursionLevel == 4) {
            return
        }
        tr_local.tr.pc.c_guiSurfs++

        // create the new matrix to draw on this surface
        tr_guisurf.R_SurfaceToTextureAxis(drawSurf.geo, origin, axis)
        val guiModelMatrix = FloatArray(16)
        val modelMatrix = FloatArray(16)
        guiModelMatrix[0] = axis[0].get(0) / 640.0f
        guiModelMatrix[4] = axis[1].get(0) / 480.0f
        guiModelMatrix[8] = axis[2].get(0)
        guiModelMatrix[12] = origin.get(0)
        guiModelMatrix[1] = axis[0].get(1) / 640.0f
        guiModelMatrix[5] = axis[1].get(1) / 480.0f
        guiModelMatrix[9] = axis[2].get(1)
        guiModelMatrix[13] = origin.get(1)
        guiModelMatrix[2] = axis[0].get(2) / 640.0f
        guiModelMatrix[6] = axis[1].get(2) / 480.0f
        guiModelMatrix[10] = axis[2].get(2)
        guiModelMatrix[14] = origin.get(2)
        guiModelMatrix[3] = 0
        guiModelMatrix[7] = 0
        guiModelMatrix[11] = 0
        guiModelMatrix[15] = 1
        tr_main.myGlMultMatrix(
            guiModelMatrix, drawSurf.space.modelMatrix,
            modelMatrix
        )
        tr_local.tr.guiRecursionLevel++

        // call the gui, which will call the 2D drawing functions
        tr_local.tr.guiModel.Clear()
        gui.Redraw(tr_local.tr.viewDef.renderView.time)
        tr_local.tr.guiModel.EmitToCurrentView(modelMatrix, drawSurf.space.weaponDepthHack)
        tr_local.tr.guiModel.Clear()
        tr_local.tr.guiRecursionLevel--
    }

    /*
     ================,
     R_ReloadGuis_f

     Reloads any guis that have had their file timestamps changed.
     An optional "all" parameter will cause all models to reload, even
     if they are not out of date.

     Should we also reload the map models?
     ================
     */
    class R_ReloadGuis_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            val all: Boolean
            if (0 == idStr.Companion.Icmp(args.Argv(1), "all")) {
                all = true
                Common.common.Printf("Reloading all gui files...\n")
            } else {
                all = false
                Common.common.Printf("Checking for changed gui files...\n")
            }
            UserInterface.uiManager.Reload(all)
        }

        companion object {
            private val instance: cmdFunction_t? = R_ReloadGuis_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }

    /*
     ================,
     R_ListGuis_f

     ================
     */
    class R_ListGuis_f private constructor() : cmdFunction_t() {
        override fun run(args: CmdArgs.idCmdArgs?) {
            UserInterface.uiManager.ListGuis()
        }

        companion object {
            private val instance: cmdFunction_t? = R_ListGuis_f()
            fun getInstance(): cmdFunction_t? {
                return instance
            }
        }
    }
}