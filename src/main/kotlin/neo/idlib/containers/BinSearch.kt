package neo.idlib.containers

import neo.framework.DeclAF.idAFVector.type

object BinSearch {
    /*
     ===============================================================================

     Binary Search templates

     The array elements have to be ordered in increasing order.

     ===============================================================================
     */
    /*
     ====================
     idBinSearch_GreaterEqual

     Finds the last array element which is smaller than the given value.
     ====================
     */
    fun <type> idBinSearch_Less(array: Array<type?>?, arraySize: Int, value: type?): Int {
        var len = arraySize
        var mid = len
        var offset = 0
        while (mid > 0) {
            mid = len shr 1
            if (BinSearch.LT(array.get(offset + mid),  /*<*/value)) {
                offset += mid
            }
            len -= mid
        }
        return offset
    }

    /*
     ====================
     idBinSearch_GreaterEqual

     Finds the last array element which is smaller than or equal to the given value.
     ====================
     */
    fun <type> idBinSearch_LessEqual(array: Array<type?>?, arraySize: Int, value: type?): Int {
        var len = arraySize
        var mid = len
        var offset = 0
        while (mid > 0) {
            mid = len shr 1
            if (BinSearch.LTE(array.get(offset + mid),  /*<=*/value)) {
                offset += mid
            }
            len -= mid
        }
        return offset
    }

    /*
     ====================
     idBinSearch_Greater

     Finds the first array element which is greater than the given value.
     ====================
     */
    fun <type> idBinSearch_Greater(array: Array<type?>?, arraySize: Int, value: type?): Int {
        var len = arraySize
        var mid = len
        var offset = 0
        var res = 0
        while (mid > 0) {
            mid = len shr 1
            if (BinSearch.GT(array.get(offset + mid),  /*>*/value)) {
                res = 0
            } else {
                offset += mid
                res = 1
            }
            len -= mid
        }
        return offset + res
    }

    /*
     ====================
     idBinSearch_GreaterEqual

     Finds the first array element which is greater than or equal to the given value.
     ====================
     */
    fun <type> idBinSearch_GreaterEqual(array: Array<type?>?, arraySize: Int, value: type?): Int {
        var len = arraySize
        var mid = len
        var offset = 0
        var res = 0
        while (mid > 0) {
            mid = len shr 1
            if (BinSearch.GTE(array.get(offset + mid),  /*>=*/value)) {
                res = 0
            } else {
                offset += mid
                res = 1
            }
            len -= mid
        }
        return offset + res
    }

    fun GT(object1: Any?, object2: Any?): Boolean {
        return (object1 as Number?).toDouble() > (object2 as Number?).toDouble()
    }

    //Greater Than or Equal
    fun GTE(object1: Any?, object2: Any?): Boolean {
        return (object1 as Number?).toDouble() >= (object2 as Number?).toDouble()
    }

    //Less Than
    fun LT(object1: Any?, object2: Any?): Boolean {
        return (object1 as Number?).toDouble() < (object2 as Number?).toDouble()
    }

    //Less Than or Equal
    fun LTE(object1: Any?, object2: Any?): Boolean {
        return (object1 as Number?).toDouble() <= (object2 as Number?).toDouble()
    }
}