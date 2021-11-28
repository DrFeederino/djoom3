package neo.idlib.containers

import neo.framework.DeclAF.idAFVector.type

/**
 * //TODO:implement starcraft linked list
 */
class LinkList {
    /*
     ==============================================================================

     idLinkList

     Circular linked list template

     ==============================================================================
     */
    class idLinkList<type>() {
        private val DBG_count = DBG_counter++
        private var head: idLinkList<*>?
        private var next: idLinkList<*>?
        private var owner: type? = null
        private var prev: idLinkList<*>?

        constructor(owner: type?) : this() {
            this.owner = owner
        }

        //public						~idLinkList();
        //
        /*
         ================
         idLinkList<type>::IsListEmpty

         Returns true if the list is empty.
         ================
         */
        fun IsListEmpty(): Boolean {
            return head.next === head
        }

        /*
         ================
         idLinkList<type>::InList

         Returns true if the node is in a list.  If called on the head of a list, will always return false.
         ================
         */
        fun InList(): Boolean {
            return head !== this
        }

        /*
         ================
         idLinkList<type>::Num

         Returns the number of nodes in the list.
         ================
         */
        fun Num(): Int {
            var node: idLinkList<type?>?
            var num: Int
            num = 0
            node = head.next
            while (node !== head) {
                num++
                node = node.next
            }
            return num
        }

        /*
         ================
         idLinkList<type>::Clear

         If node is the head of the list, clears the list.  Otherwise it just removes the node from the list.
         ================
         */
        fun Clear() {
            if (head === this) {
                while (next !== this) {
                    next.Remove()
                }
            } else {
                Remove()
            }
        }

        /*
         ================
         idLinkList<type>::InsertBefore

         Places the node before the existing node in the list.  If the existing node is the head,
         then the new node is placed at the end of the list.
         ================
         */
        fun InsertBefore(node: idLinkList<*>?) {
            Remove()
            next = node
            prev = node.prev
            node.prev = this
            prev.next = this
            head = node.head
        }

        /*
         ================
         idLinkList<type>::InsertAfter

         Places the node after the existing node in the list.  If the existing node is the head,
         then the new node is placed at the beginning of the list.
         ================
         */
        fun InsertAfter(node: idLinkList<*>?) {
            Remove()
            prev = node
            next = node.next
            node.next = this
            next.prev = this
            head = node.head
        }

        /*
         ================
         idLinkList<type>::AddToEnd

         Adds node at the end of the list
         ================
         */
        fun AddToEnd(node: idLinkList<*>?) {
            InsertBefore(node.head)
        }

        /*
         ================
         idLinkList<type>::AddToFront

         Adds node at the beginning of the list
         ================
         */
        fun AddToFront(node: idLinkList<*>?) {
            InsertAfter(node.head)
        }

        /*
         ================
         idLinkList<type>::Remove

         Removes node from list
         ================
         */
        fun Remove() {
            prev.next = next
            next.prev = prev
            next = this
            prev = this
            head = this
        }

        /*
         ================
         idLinkList<type>::Next

         Returns the next object in the list, or NULL if at the end.
         ================
         */
        fun Next(): type? {
            return if (null == next || next === head) {
                null
            } else next.owner as type?
        }

        /*
         ================
         idLinkList<type>::Prev

         Returns the previous object in the list, or NULL if at the beginning.
         ================
         */
        fun Prev(): type? {
            return if (null == prev || prev === head) {
                null
            } else prev.owner as type?
        }

        //
        /*
         ================
         idLinkList<type>::Owner

         Gets the object that is associated with this node.
         ================
         */
        fun Owner(): type? {
            return owner
        }

        /*
         ================
         idLinkList<type>::SetOwner

         Sets the object that this node is associated with.
         ================
         */
        fun SetOwner(`object`: type?) {
            owner = `object`
        }

        //
        /*
         ================
         idLinkList<type>::ListHead

         Returns the head of the list.  If the node isn't in a list, it returns
         a pointer to itself.
         ================
         */
        fun ListHead(): idLinkList<*>? {
            return head
        }

        /*
         ================
         idLinkList<type>::NextNode

         Returns the next node in the list, or NULL if at the end.
         ================
         */
        fun NextNode(): idLinkList<*>? {
            return if (next === head) {
                null
            } else next
        }

        /*
         ================
         idLinkList<type>::PrevNode

         Returns the previous node in the list, or NULL if at the beginning.
         ================
         */
        fun PrevNode(): idLinkList<*>? {
            return if (prev === head) {
                null
            } else prev
        }

        companion object {
            //
            //
            private var DBG_counter = 0
        }

        /*
         ================
         idLinkList<type>::idLinkList

         Node is initialized to be the head of an empty list
         ================
         */
        init {
            head = this
            next = this
            prev = this
        }
    }
}