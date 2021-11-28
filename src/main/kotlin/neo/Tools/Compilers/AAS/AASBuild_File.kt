package neo.Tools.Compilers.AAS

import neo.idlib.BV.Bounds.idBounds
import neo.idlib.containers.HashIndex.idHashIndex

/**
 *
 */
object AASBuild_File {
    const val AAS_PLANE_DIST_EPSILON = 0.01f

    //
    const val AAS_PLANE_NORMAL_EPSILON = 0.00001f
    const val EDGE_HASH_SIZE = 1 shl 14

    //
    const val INTEGRAL_EPSILON = 0.01f
    const val VERTEX_EPSILON = 0.1f
    const val VERTEX_HASH_BOXSIZE = 1 shl 6 // must be power of 2
    const val VERTEX_HASH_SIZE = AASBuild_File.VERTEX_HASH_BOXSIZE * AASBuild_File.VERTEX_HASH_BOXSIZE
    var aas_edgeHash: idHashIndex? = null
    var aas_vertexBounds: idBounds? = null

    //
    //
    var aas_vertexHash: idHashIndex? = null
    var aas_vertexShift = 0

    internal class sizeEstimate_s {
        var numAreas = 0
        var numEdgeIndexes = 0
        var numFaceIndexes = 0
        var numNodes = 0
    }
}