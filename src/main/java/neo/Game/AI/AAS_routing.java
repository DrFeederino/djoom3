package neo.Game.AI;

import neo.idlib.BV.Bounds.idBounds;
import neo.idlib.containers.List.idList;
import neo.idlib.math.Vector.idVec3;

import java.nio.IntBuffer;

/**
 *
 */
public class AAS_routing {

    static final int CACHETYPE_AREA = 1;
    static final int CACHETYPE_PORTAL = 2;
    //
    static final int LEDGE_TRAVELTIME_PANALTY = 250;
    //
    static final int MAX_ROUTING_CACHE_MEMORY = (2 * 1024 * 1024);
//

    static class idRoutingCache {
        // friend class idAASLocal;
        public static final int BYTES = Integer.BYTES * 12;
        int areaNum;         // area of the cache
        int cluster;         // cluster of the cache
        idRoutingCache next;            // next in list
        idRoutingCache prev;            // previous in list
        byte[] reachabilities;  // reachabilities used for routing
        int size;            // size of cache
        int startTravelTime; // travel time to start with
        idRoutingCache time_next;       // next in time based list
        idRoutingCache time_prev;       // previous in time based list
        int travelFlags;     // combinations of the travel flags
        int[] travelTimes;     // travel time for every area
        int type;            // portal or area cache
        //
        //

        public idRoutingCache(int size) {
            areaNum = 0;
            cluster = 0;
            next = prev = null;
            time_next = time_prev = null;
            travelFlags = 0;
            startTravelTime = 0;
            type = 0;
            this.size = size;
            reachabilities = new byte[size];
//	memset( reachabilities, 0, size * sizeof( reachabilities[0] ) );
            travelTimes = new int[size];
//	memset( travelTimes, 0, size * sizeof( travelTimes[0] ) );
        }

        // ~idRoutingCache( void );
        public int Size() {
            return idRoutingCache.BYTES + size * Byte.BYTES + size * Short.BYTES;//TODO:we use integers for travelTimes, but are using shorts for the sake of consistency...
        }
    }

    static class idRoutingUpdate {
        // friend class idAASLocal;

        int areaNum;         // area number of this update
        IntBuffer areaTravelTimes; // travel times within the area
        int cluster;         // cluster number of this update
        boolean isInList;        // true if the update is in the list
        idRoutingUpdate next;            // next in list
        idRoutingUpdate prev;            // prev in list
        idVec3 start;           // start point into area
        int tmpTravelTime;   // temporary travel time
        //
        //
    }

    static class idRoutingObstacle {
        // friend class idAASLocal;

        idList<Integer> areas;           // areas the bounds are in
        idBounds bounds;          // obstacle bounds
        //
        //

        idRoutingObstacle() {
        }
    }

}
