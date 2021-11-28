package neo.idlib.containers

import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.idList
import neo.idlib.math.Plane
import neo.idlib.math.Plane.idPlane

/**
 *
 */
class PlaneSet {
    /*
     ===============================================================================

     Plane Set

     ===============================================================================
     */
    class idPlaneSet : idList<idPlane?>() {
        private val hash: idHashIndex?
        override fun Clear() {
            super.Clear()
            hash.Free()
        }

        //
        fun FindPlane(plane: idPlane?, normalEps: Float, distEps: Float): Int {
            var i: Int
            var border: Int
            val hashKey: Int
            assert(distEps <= 0.125f)
            hashKey = (Math.abs(plane.Dist()) * 0.125f).toInt()
            border = -1
            while (border <= 1) {
                i = hash.First(hashKey + border)
                while (i >= 0) {
                    if (oGet(i).Compare(plane, normalEps, distEps)) {
                        return i
                    }
                    i = hash.Next(i)
                }
                border++
            }
            return if (plane.Type() >= Plane.PLANETYPE_NEGX && plane.Type() < Plane.PLANETYPE_TRUEAXIAL) {
                Append(plane.oNegative())
                hash.Add(hashKey, Num() - 1)
                Append(plane)
                hash.Add(hashKey, Num() - 1)
                Num() - 1
            } else {
                Append(plane)
                hash.Add(hashKey, Num() - 1)
                Append(plane.oNegative())
                hash.Add(hashKey, Num() - 1)
                Num() - 2
            }
        }

        //
        //
        init {
            hash = idHashIndex()
        }
    }
}