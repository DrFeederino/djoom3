package neo.idlib.containers

import neo.idlib.containers.Stack.idStackTemplate.Companion.STACK_BLOCK_SIZE


/**
 *
 */
class Stack {
    /*
     ===============================================================================

     Stack template

     ===============================================================================
     */
    inner class idStackTemplate<T>  //
    //
    {
        private var stack: Array<T?> = arrayOfNulls<Any?>(10) as Array<T?>
        private var top = 0

        //
        fun Add(element: T?) { //push
            if (top >= stack.size) { //reached top of stack
                expand()
            }
            stack.get(top++) = element
        }

        fun Get(): T? { //pop
            if (top < 0) { //reached bottom
                return null
            }
            if (stack.size - STACK_BLOCK_SIZE > 0 && top < stack.size - STACK_BLOCK_SIZE) { //reached block threshold
                shrink()
            }
            return stack.get(top--)
        }

        private fun expand() {
            val temp = stack
            stack = arrayOfNulls<Any?>(stack.size + STACK_BLOCK_SIZE) as Array<T?>
            System.arraycopy(temp, 0, stack, 0, temp.size)
        }

        private fun shrink() {
            val temp = stack
            stack = arrayOfNulls<Any?>(stack.size - STACK_BLOCK_SIZE) as Array<T?>
            System.arraycopy(temp, 0, stack, 0, stack.size)
        }

        companion object {
            //        private T bottom;
            //
            private const val STACK_BLOCK_SIZE = 10
        }
    }
}