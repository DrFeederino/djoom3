package neo.idlib.containers

import neo.framework.DeclAF.idAFVector.type
import java.util.*

/**
 *
 */
class Queue {
    //TODO:test this
    /*
     ===============================================================================

     Queue template

     ===============================================================================
     */
    //#define idQueue( type, next )		idQueueTemplate<type, (int)&(((type*)NULL)->next)>
    class idQueueTemplate<type>  //TODO:fix the nextOffset part.
    //        private final static int QUEUE_BLOCK_SIZE = 10;
    //        //
    //        private int first;
    //        private int last;
    //        private type[] queue      = (type[]) new Object[10];
    //        private int    nextOffset = 0;
    //        //
    //        //
        : LinkedList<type?>() {
        fun Add(element: type?): Boolean {
//            if (nextOffset >= queue.length) {
////		QUEUE_NEXT_PTR(last) = element;
//                expandQueue();
//            }
//            queue[nextOffset] = element;
////            last = element;
//            nextOffset++;
            return super.add(element)
        }

        fun Get(): type? {
            return if (super.isEmpty()) {
                null
            } else super.pop()
            //
//            if (nextOffset <= queue.length - QUEUE_BLOCK_SIZE) {
//                shrinkQueue();
//            }
//
//            return queue[--nextOffset];
        } //        private void expandQueue() {
        //            final type[] tempQueue = queue;
        //            queue = (type[]) new Object[queue.length + QUEUE_BLOCK_SIZE];
        //
        //            System.arraycopy(tempQueue, 0, queue, 0, tempQueue.length);
        //        }
        //
        //        private void shrinkQueue() {
        //            final type[] tempQueue = queue;
        //            queue = (type[]) new Object[queue.length - QUEUE_BLOCK_SIZE];
        //
        //            System.arraycopy(tempQueue, 0, queue, 0, queue.length);
        //        }
    }
}