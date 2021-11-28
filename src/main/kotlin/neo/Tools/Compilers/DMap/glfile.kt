package neo.Tools.Compilers.DMap

import neo.TempDump
import neo.Tools.Compilers.DMap.dmap.node_s
import neo.Tools.Compilers.DMap.dmap.tree_s
import neo.Tools.Compilers.DMap.dmap.uPortal_s
import neo.framework.Common
import neo.framework.FileSystem_h
import neo.framework.File_h.idFile
import neo.idlib.geometry.Winding.idWinding

/**
 *
 */
object glfile {
    var c_glfaces = 0
    private const val level = 128
    fun PortalVisibleSides(p: uPortal_s?): Int {
        val fcon: Boolean
        val bcon: Boolean
        if (TempDump.NOT(p.onnode)) {
            return 0 // outside
        }
        fcon = p.nodes[0].opaque
        bcon = p.nodes[1].opaque

        // same contents never create a face
        if (fcon == bcon) {
            return 0
        }
        if (!fcon) {
            return 1
        }
        return if (!bcon) {
            2
        } else 0
    }

    fun OutputWinding(w: idWinding?, glview: idFile?) {
        val light: Float
        var i: Int
        glview.WriteFloatString("%d\n", w.GetNumPoints())
        glfile.level += 28
        light = (glfile.level and 255) / 255.0f
        i = 0
        while (i < w.GetNumPoints()) {
            glview.WriteFloatString(
                "%6.3f %6.3f %6.3f %6.3f %6.3f %6.3f\n",
                w.oGet(i, 0),
                w.oGet(i, 1),
                w.oGet(i, 2),
                light,
                light,
                light
            )
            i++
        }
        glview.WriteFloatString("\n")
    }

    /*
     =============
     OutputPortal
     =============
     */
    fun OutputPortal(p: uPortal_s?, glview: idFile?) {
        var w: idWinding?
        val sides: Int
        sides = glfile.PortalVisibleSides(p)
        if (0 == sides) {
            return
        }
        glfile.c_glfaces++
        w = p.winding
        if (sides == 2) {        // back side
            w = w.Reverse()
        }
        glfile.OutputWinding(w, glview)

//	if ( sides == 2 ) {
//		delete w;
//	}
    }

    /*
     =============
     WriteGLView_r
     =============
     */
    fun WriteGLView_r(node: node_s?, glview: idFile?) {
        var p: uPortal_s?
        var nextp: uPortal_s?
        if (node.planenum != dmap.PLANENUM_LEAF) {
            glfile.WriteGLView_r(node.children[0], glview)
            glfile.WriteGLView_r(node.children[1], glview)
            return
        }

        // write all the portals
        p = node.portals
        while (p != null) {
            nextp = if (p.nodes[0] === node) {
                glfile.OutputPortal(p, glview)
                p.next[0]
            } else {
                p.next[1]
            }
            p = nextp
        }
    }

    /*
     =============
     WriteGLView
     =============
     */
    fun WriteGLView(tree: tree_s?, source: String?) {
        val glview: idFile?
        glfile.c_glfaces = 0
        Common.common.Printf("Writing %s\n", source)
        glview = FileSystem_h.fileSystem.OpenExplicitFileWrite(source)
        if (TempDump.NOT(glview)) {
            Common.common.Error("Couldn't open %s", source)
        }
        glfile.WriteGLView_r(tree.headnode, glview)
        FileSystem_h.fileSystem.CloseFile(glview)
        Common.common.Printf("%5d c_glfaces\n", glfile.c_glfaces)
    }
}