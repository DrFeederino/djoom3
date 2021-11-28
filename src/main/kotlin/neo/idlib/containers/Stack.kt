package neo.idlib.containers

import neo.framework.DeclAF.idAFVector.type

/**
 *
 */
class Stack {
    /*
     ===============================================================================

     Stack template

     ===============================================================================
     */
    internal inner class idStackTemplate<type>  //
    //
    {
        private var stack: Array<type?>? = arrayOfNulls<Any?>(10) as Array<type?>
        private var top = 0

        //
        fun Add(element: type?) { //push
            if (top >= stack.size) { //reached top of stack
                expand()
            }
            stack.get(top++) = element
        }

        fun Get(): type? { //pop
            if (top < 0) { //reached bottom
                return null
            }
            if (stack.size - Companion.STACK_BLOCK_SIZE > 0 && top < stack.size - Companion.STACK_BLOCK_SIZE) { //reached block threshold
                shrink()
            }
            return stack.get(top--)
        }

        private fun expand() {
            val temp = stack
            stack = arrayOfNulls<Any?>(stack.size + Companion.STACK_BLOCK_SIZE) as Array<type?>
            System.arraycopy(temp, 0, stack, 0, temp.size)
        }

        private fun shrink() {
            val temp = stack
            stack = arrayOfNulls<Any?>(stack.size - Companion.STACK_BLOCK_SIZE) as Array<type?>
            System.arraycopy(temp, 0, stack, 0, stack.size)
        }

        companion object {
            //        private type bottom;
            //
            private const val STACK_BLOCK_SIZE = 10
        }
    }
}