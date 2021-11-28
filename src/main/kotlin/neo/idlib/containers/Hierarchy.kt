package neo.idlib.containers

import neo.framework.DeclAF.idAFVector.type
import neo.idlib.Lib.idLib

/**
 *
 */
class Hierarchy {
    /*
     ==============================================================================

     idHierarchy

     ==============================================================================
     */
    class idHierarchy<type>     //
    //
    {
        private var child: idHierarchy<*>? = null
        private var owner: type? = null
        private var parent: idHierarchy<*>? = null
        private var sibling: idHierarchy<*>? = null

        //public						~idHierarchy();
        //	
        /*
         ================
         idHierarchy<type>::SetOwner

         Sets the object that this node is associated with.
         ================
         */
        fun SetOwner(`object`: type?) {
            owner = `object`
        }

        /*
         ================
         idHierarchy<type>::Owner

         Gets the object that is associated with this node.
         ================
         */
        fun Owner(): type? {
            return owner
        }

        /*
         ================
         idHierarchy<type>::ParentTo

         Makes the given node the parent.
         ================
         */
        fun ParentTo(node: idHierarchy<*>?) {
            RemoveFromParent()
            parent = node
            sibling = node.child
            node.child = this
        }

        /*
         ================
         idHierarchy<type>::MakeSiblingAfter

         Makes the given node a sibling after the passed in node.
         ================
         */
        fun MakeSiblingAfter(node: idHierarchy<*>?) {
            RemoveFromParent()
            parent = node.parent
            sibling = node.sibling
            node.sibling = this
        }

        fun ParentedBy(node: idHierarchy<*>?): Boolean {
            if (parent === node) {
                return true
            } else if (parent != null) {
                return parent.ParentedBy(node)
            }
            return false
        }

        fun RemoveFromParent() {
            val prev: idHierarchy<type?>?
            if (parent != null) {
                prev = GetPriorSiblingNode()
                if (prev != null) {
                    prev.sibling = sibling
                } else {
                    parent.child = sibling
                }
            }
            parent = null
            sibling = null
        }

        /*
         ================
         idHierarchy<type>::RemoveFromHierarchy

         Removes the node from the hierarchy and adds it's children to the parent.
         ================
         */
        fun RemoveFromHierarchy() {
            val parentNode: idHierarchy<type?>?
            var node: idHierarchy<type?>?
            parentNode = parent
            RemoveFromParent()
            if (parentNode != null) {
                while (child != null) {
                    node = child
                    node.RemoveFromParent()
                    node.ParentTo(parentNode)
                }
            } else {
                while (child != null) {
                    child.RemoveFromParent()
                }
            }
        }

        // parent of this node
        fun GetParent(): type? {
            return if (parent != null) {
                parent.owner as type?
            } else null
        }

        // first child of this node
        fun GetChild(): type? {
            return if (child != null) {
                child.owner as type?
            } else null
        }

        // next node with the same parent
        fun GetSibling(): type? {
            return if (sibling != null) {
                sibling.owner as type?
            } else null
        }

        /*
         ================
         idHierarchy<type>::GetPriorSiblingNode

         Returns NULL if no parent, or if it is the first child.
         ================
         */
        fun GetPriorSibling(): type? {        // previous node with the same parent
            if (null == parent || parent.child === this) {
                return null
            }
            var prev: idHierarchy<type?>?
            var node: idHierarchy<type?>?
            node = parent.child
            prev = null
            while (node !== this && node != null) {
                prev = node
                node = node.sibling
            }
            if (node !== this) {
                idLib.Error("idHierarchy::GetPriorSibling: could not find node in parent's list of children")
            }
            return prev as type?
        }

        /*
         ================
         idHierarchy<type>::GetNext

         Goes through all nodes of the hierarchy.
         ================
         */
        fun GetNext(): type? {            // goes through all nodes of the hierarchy
            var node: idHierarchy<type?>?
            return if (child != null) {
                child.owner as type?
            } else {
                node = this
                while (node != null && null == node.sibling) {
                    node = node.parent
                }
                if (node != null) {
                    node.sibling.owner as type?
                } else {
                    null
                }
            }
        }

        /*
         ================
         idHierarchy<type>::GetNextLeaf

         Goes through all leaf nodes of the hierarchy.
         ================
         */
        fun GetNextLeaf(): type? {        // goes through all leaf nodes of the hierarchy
            var node: idHierarchy<type?>?
            return if (child != null) {
                node = child
                while (node.child != null) {
                    node = node.child
                }
                node.owner
            } else {
                node = this
                while (node != null && null == node.sibling) {
                    node = node.parent
                }
                if (node != null) {
                    node = node.sibling
                    while (node.child != null) {
                        node = node.child
                    }
                    node.owner
                } else {
                    null
                }
            }
        }

        /*
         ================
         idHierarchy<type>::GetPriorSibling

         Returns NULL if no parent, or if it is the first child.
         ================
         */
        private fun GetPriorSiblingNode(): idHierarchy<type?>? { // previous node with the same parent
            val prior: idHierarchy<type?>?
            prior = GetPriorSiblingNode()
            return if (prior != null) {
                prior.owner as idHierarchy<type?>?
            } else null
        }
    }
}