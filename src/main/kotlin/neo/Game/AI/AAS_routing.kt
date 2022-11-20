package neo.Game.AI

import neo.idlib.BV.Bounds.idBounds
import neo.idlib.containers.List.idList
import neo.idlib.math.Vector.idVec3
import java.nio.IntBuffer

/**
 *
 */
object AAS_routing {
    const val CACHETYPE_AREA = 1
    const val CACHETYPE_PORTAL = 2

    //
    const val LEDGE_TRAVELTIME_PANALTY = 250

    //
    const val MAX_ROUTING_CACHE_MEMORY = 2 * 1024 * 1024

    //
    internal class idRoutingCache(size: Int) {
        var areaNum // area of the cache
                = 0
        var cluster // cluster of the cache
                = 0
        var next // next in list
                : idRoutingCache?
        var prev // previous in list
                : idRoutingCache? = null
        var reachabilities // reachabilities used for routing
                : ByteArray
        var size // size of cache
                : Int
        var startTravelTime // travel time to start with
                : Int
        var time_next // next in time based list
                : idRoutingCache?
        var time_prev // previous in time based list
                : idRoutingCache?
        var travelFlags // combinations of the travel flags
                : Int
        var travelTimes // travel time for every area
                : IntArray
        var type // portal or area cache
                : Int

        // ~idRoutingCache( void );
        fun Size(): Int {
            return BYTES + size * java.lang.Byte.BYTES + size * java.lang.Short.BYTES //TODO:we use integers for travelTimes, but are using shorts for the sake of consistency...
        }

        companion object {
            // friend class idAASLocal;
            const val BYTES = Integer.BYTES * 12
        }

        //
        //
        init {
            next = prev
            time_prev = null
            time_next = time_prev
            travelFlags = 0
            startTravelTime = 0
            type = 0
            this.size = size
            reachabilities = ByteArray(size)
            //	memset( reachabilities, 0, size * sizeof( reachabilities[0] ) );
            travelTimes = IntArray(size)
            //	memset( travelTimes, 0, size * sizeof( travelTimes[0] ) );
        }
    }

    internal class idRoutingUpdate {
        // friend class idAASLocal;
        var areaNum // area number of this update
                = 0
        lateinit var areaTravelTimes // travel times within the area
                : IntBuffer
        var cluster // cluster number of this update
                = 0
        var isInList // true if the update is in the list
                = false
        var next // next in list
                : idRoutingUpdate? = null
        var prev // prev in list
                : idRoutingUpdate? = null
        val start: idVec3 = idVec3() // start point into area
        var tmpTravelTime // temporary travel time
                = 0 //
    }

    internal class idRoutingObstacle {
        // friend class idAASLocal;
        val areas: idList<Int> = idList() // areas the bounds are in
        val bounds // obstacle bounds
                : idBounds = idBounds()
    }
}