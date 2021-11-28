package neo.idlib.containers

import neo.TempDump
import neo.TempDump.reflects
import neo.framework.CVarSystem
import neo.framework.CVarSystem.idInternalCVar
import neo.framework.CmdSystem
import neo.framework.CmdSystem.commandDef_s
import neo.framework.DeclAF.idAFVector.type
import neo.idlib.Text.Str.idStr
import neo.idlib.containers.StrPool.idPoolStr
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 *
 */
object List {
    fun <T> idSwap(a1: Array<T>, a2: Array<T>, p1: Int, p2: Int) {
        val c = a1[p1]
        a1[p1] = a2[p2]
        a2[p2] = c
    }

    //    @Deprecated
    //    public static <T> void idSwap(T a, T b) {
    //        T c = a;
    //        a = b;
    //        b = c;
    //    }
    //
    fun idSwap(array: IntArray?, p1: Int, p2: Int) {
        neo.idlib.containers.List.idSwap(array, array, p1, p2)
    }

    fun idSwap(a1: IntArray?, a2: IntArray?, p1: Int, p2: Int) {
        val c = a1.get(p1)
        a1.get(p1) = a2.get(p2)
        a2.get(p2) = c
    }

    fun idSwap(a1: Array<IntArray?>?, p11: Int, p12: Int, a2: Array<IntArray?>?, p21: Int, p22: Int) {
        val c = a1.get(p11).get(p12)
        a1.get(p11).get(12) = a2.get(p21).get(p22)
        a2.get(p21).get(p22) = c
    }

    fun idSwap(a1: FloatArray?, a2: FloatArray?, p1: Int, p2: Int) {
        val c = a1.get(p1)
        a1.get(p1) = a2.get(p2)
        a2.get(p2) = c
    }

    fun idSwap(a: FloatArray?, b: FloatArray?) {
        val length = a.size
        val c = FloatArray(length)
        System.arraycopy(a, 0, c, 0, length)
        System.arraycopy(b, 0, a, 0, length)
        System.arraycopy(c, 0, b, 0, length)
    }

    fun idSwap(a: Array<Float?>?, b: Array<Float?>?) {
        val length = a.size
        val c = arrayOfNulls<Float?>(length)
        System.arraycopy(a, 0, c, 0, length)
        System.arraycopy(b, 0, a, 0, length)
        System.arraycopy(c, 0, b, 0, length)
    }

    interface cmp_t<type> : Comparator<type?>

    /*
     ===============================================================================

     List template
     Does not allocate memory until the first item is added.

     ===============================================================================
     */
    open class idList<type> {
        private val DBG_count = DBG_counter++
        protected var granularity = 16
        protected var num = 0
        private var list: Array<type?>?
        private var size = 0
        private var type: Class<type?>? = null

        //
        //public	typedef int		cmp_t( const type *, const type * );
        //public	typedef type	new_t( );
        //
        constructor() {
            //            this(16);//disabled to prevent inherited constructors from calling the overridden clear function.
        }

        constructor(type: Class<type?>?) : this() {
            this.type = type
        }

        constructor(newgranularity: Int) {
            assert(newgranularity > 0)
            list = null
            granularity = newgranularity
            Clear()
        }

        constructor(newgranularity: Int, type: Class<type?>?) : this(newgranularity) {
            this.type = type
        }

        constructor(other: idList<type?>?) {
            list = null
            this.oSet(other)
        }

        //public					~idList<type>( );
        //
        /*
         ================
         idList<type>::Clear

         Frees up the memory allocated by the list.  Assumes that type automatically handles freeing up memory.
         ================
         */
        open fun Clear() {                                        // clear the list
//            if (list) {
//                delete[] list;
//            }
            list = null
            num = 0
            size = 0
        }

        /*
         ================
         idList<type>::Num

         Returns the number of elements currently contained in the list.
         Note that this is NOT an indication of the memory allocated.
         ================
         */
        open fun Num(): Int {                                    // returns number of elements in list
            return num
        }

        /*
         ================
         idList<type>::NumAllocated

         Returns the number of elements currently allocated for.
         ================
         */
        fun NumAllocated(): Int {                            // returns number of elements allocated for
            return size
        }

        /*
         ================
         idList<type>::SetGranularity

         Sets the base size of the array and resizes the array to match.
         ================
         */
        fun SetGranularity(newgranularity: Int) {            // set new granularity
            var newsize: Int
            assert(newgranularity > 0)
            granularity = newgranularity
            if (list != null) {
                // resize it to the closest level of granularity
                newsize = num + granularity - 1
                newsize -= newsize % granularity
                if (newsize != size) {
                    Resize(newsize)
                }
            }
        }

        /*
         ================
         idList<type>::GetGranularity

         Get the current granularity.
         ================
         */
        fun GetGranularity(): Int {                        // get the current granularity
            return granularity
        }

        //
        /*
         ================
         idList<type>::Allocated

         return total memory allocated for the list in bytes, but doesn't take into account additional memory allocated by type
         ================
         */
        fun Allocated(): Int {                        // returns total size of allocated memory
            return size
        }

        /*size_t*/   fun Size(): Int {                        // returns total size of allocated memory including size of list type
            return Allocated()
        }

        /*size_t*/   fun MemoryUsed(): Int {                    // returns size of the used elements in the list
            return num /* sizeof( *list )*/
        }

        /*
         ================
         idList<type>::operator=

         Copies the contents and size attributes of another list.
         ================
         */
        fun oSet(other: idList<type?>?): idList<type?>? {
            var i: Int
            Clear()
            num = other.num
            size = other.size
            granularity = other.granularity
            type = other.type
            if (size != 0) {
                list = arrayOfNulls<Any?>(size) as Array<type?>
                i = 0
                while (i < num) {
                    list.get(i) = other.list.get(i)
                    i++
                }
            }
            return this
        }

        /*
         ================
         idList<type>::operator[] const

         Access operator.  Index must be within range or an assert will be issued in debug builds.
         Release builds do no range checking.
         ================
         */
        fun oGet(index: Int): type {
            assert(index >= 0)
            assert(index < num)
            return list!![index] as type
        }

        //public	type &			operator[]( int index );
        //
        //        public type oSet(int index, type value) {
        //            assert (index >= 0);
        //            assert (index < num);
        //
        //            return list[index] = value;
        //        }
        fun oSet(index: Int, value: Any?): type {
            assert(index >= 0)
            assert(index < num)
            return value as type?. also { list.get(index) = it }
        }

        fun oPluSet(index: Int, value: type?): type? {
            assert(index >= 0)
            assert(index < num)

//            if (list[index] instanceof Double) {
//                return list[index] = (type) (Object) ((Double) list[index] + (Double) value);//TODO:test thsi shit
//            }
//            if (list[index] instanceof Float) {
//                return list[index] = (type) (Object) ((Float) list[index] + (Float) value);//TODO:test thsi shit
//            }
//            if (list[index] instanceof Integer) {
            return ((list.get(index) as Number?).toDouble() + (value as Number?).toDouble()) as type?. also {
                list.get(index) = it //TODO:test thsi shit
            }
            //            }
        }

        //
        /*
         ================
         idList<type>::Condense

         Resizes the array to exactly the number of elements it contains or frees up memory if empty.
         ================
         */
        fun Condense() {                                    // resizes list to exactly the number of elements it contains
            if (list != null) {
                if (num != 0) {
                    Resize(num)
                } else {
                    Clear()
                }
            }
        }

        /*
         ================
         idList<type>::Resize

         Allocates memory for the amount of elements requested while keeping the contents intact.
         Contents are copied using their = operator so that data is correnctly instantiated.
         ================
         */
        fun Resize(newsize: Int) {                                // resizes list to the given number of elements
            val temp: Array<type?>?
            var i: Int
            assert(newsize >= 0)

            // free up the list if no data is being reserved
            if (newsize <= 0) {
                Clear()
                return
            }
            if (newsize == size) {
                // not changing the size, so just exit
                return
            }
            temp = list
            size = newsize
            if (size < num) {
                num = size
            }

            // copy the old list into our new one
            list = arrayOfNulls<Any?>(size) as Array<type?>
            i = 0
            while (i < num) {
                list.get(i) = temp.get(i)
                i++
            }

            // delete the old list if it exists
//	if ( temp ) {
//		delete[] temp;
//	}
        }

        /*
         ================
         idList<type>::Resize

         Allocates memory for the amount of elements requested while keeping the contents intact.
         Contents are copied using their = operator so that data is correnctly instantiated.
         ================
         */
        fun Resize(newsize: Int, newgranularity: Int) {            // resizes list and sets new granularity
            val temp: Array<type?>?
            var i: Int
            assert(newsize >= 0)
            assert(newgranularity > 0)
            granularity = newgranularity

            // free up the list if no data is being reserved
            if (newsize <= 0) {
                Clear()
                return
            }
            temp = list
            size = newsize
            if (size < num) {
                num = size
            }

            // copy the old list into our new one
            list = arrayOfNulls<Any?>(size) as Array<type?>
            i = 0
            while (i < num) {
                list.get(i) = temp.get(i)
                i++
            }

            // delete the old list if it exists
//	if ( temp ) {
//		delete[] temp;
//	}
        }

        /*
         ================
         idList<type>::SetNum

         Resize to the exact size specified irregardless of granularity
         ================
         */
        @JvmOverloads
        fun SetNum(
            newnum: Int,
            resize: Boolean = true
        ) {            // set number of elements in list and resize to exactly this number if necessary
            assert(newnum >= 0)
            if (resize || newnum > size) {
                Resize(newnum)
            }
            num = newnum
        }

        /*
         ================
         idList<type>::AssureSize

         Makes sure the list has at least the given number of elements.
         ================
         */
        fun AssureSize(newSize: Int) {                            // assure list has given number of elements, but leave them uninitialized
            var newSize = newSize
            val newNum = newSize
            if (newSize > size) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newSize += granularity - 1
                newSize -= newSize % granularity
                Resize(newSize)
            }
            num = newNum
        }

        /*
         ================
         idList<type>::AssureSize

         Makes sure the list has at least the given number of elements and initialize any elements not yet initialized.
         ================
         */
        fun AssureSize(
            newSize: Int,
            initValue: type?
        ) {    // assure list has given number of elements and initialize any new elements
            var newSize = newSize
            val newNum = newSize
            if (newSize > size) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newSize += granularity - 1
                newSize -= newSize % granularity
                num = size
                Resize(newSize)
                for (i in num until newSize) {
                    list.get(i) = initValue
                }
            }
            num = newNum
        }

        /*
         ================
         idList<type>::AssureSizeAlloc

         Makes sure the list has at least the given number of elements and allocates any elements using the allocator.

         NOTE: This function can only be called on lists containing pointers. Calling it
         on non-pointer lists will cause a compiler error.
         ================
         */
        fun AssureSizeAlloc(
            newSize: Int,  /*new_t*/
            allocator: Class<*>?
        ) {    // assure the pointer list has the given number of elements and allocate any new elements
            var newSize = newSize
            val newNum = newSize
            if (newSize > size) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newSize += granularity - 1
                newSize -= newSize % granularity
                num = size
                Resize(newSize)
                for (i in num until newSize) {
                    try {
                        list.get(i) =  /*( * allocator) ()*/
                            allocator.newInstance() as type //TODO: check if any of this is necessary?
                    } catch (ex: InstantiationException) {
                        Logger.getLogger(List::class.java.name).log(Level.SEVERE, null, ex)
                    } catch (ex: IllegalAccessException) {
                        Logger.getLogger(List::class.java.name).log(Level.SEVERE, null, ex)
                    }
                }
            }
            num = newNum
        }

        //
        /*
         ================
         idList<type>::Ptr

         Returns a pointer to the begining of the array.  Useful for iterating through the list in loops.

         Note: may return NULL if the list is empty.

         FIXME: Create an iterator template for this kind of thing.
         ================
         */
        @Deprecated("")
        fun getList(): Array<type?>? {                                        // returns a pointer to the list
            return list
        }

        fun <T> getList(type: Class<out Array<T?>?>?): Array<T?>? {
            return if (num == 0) null else Arrays.copyOf(list, num, type)

            // returns a pointer to the list
        }

        //public	const type *	Ptr( ) const;									// returns a pointer to the list
        /*
         ================
         idList<type>::Alloc

         Returns a reference to a new data element at the end of the list.
         ================
         */
        fun Alloc(): type? {                                    // returns reference to a new data element at the end of the list
            if (TempDump.NOT(*list)) {
                Resize(granularity)
            }
            if (num == size) {
                Resize(size + granularity)
            }
            try {
                return type.newInstance().also { list.get(num++) = it }
            } catch (ex: InstantiationException) {
//                Logger.getLogger(List.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ex: IllegalAccessException) {
            }
            return null
        }

        /*
         ================
         idList<type>::Append

         Increases the size of the list by one element and copies the supplied data into it.

         Returns the index of the new element.
         ================
         */
        fun Append(obj: type?): Int { // append element
            if (TempDump.NOT(*list)) {
                Resize(granularity)
            }
            if (num == size) {
                val newsize: Int
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newsize = size + granularity
                Resize(newsize - newsize % granularity)
            }
            list.get(num) = obj
            num++
            return num - 1
        }

        /*
         ================
         idList<type>::Append

         adds the other list to this one

         Returns the size of the new combined list
         ================
         */
        fun Append(other: idList<type?>?): Int {                // append list
            if (TempDump.NOT(*list)) {
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                Resize(granularity)
            }
            val n = other.Num()
            for (i in 0 until n) {
                Append(other.oGet(i))
            }
            return Num()
        }

        /*
         ================
         idList<type>::AddUnique

         Adds the data to the list if it doesn't already exist.  Returns the index of the data in the list.
         ================
         */
        fun AddUnique(obj: type?): Int {            // add unique element
            var index: Int
            index = FindIndex(obj)
            if (index < 0) {
                index = Append(obj)
            }
            return index
        }

        /*
         ================
         idList<type>::Insert

         Increases the size of the list by at leat one element if necessary
         and inserts the supplied data into it.

         Returns the index of the new element.
         ================
         */
        @JvmOverloads
        fun Insert(obj: type?, index: Int = 0): Int {            // insert the element at the given index
            var index = index
            if (TempDump.NOT(*list)) {
                Resize(granularity)
            }
            if (num == size) {
                val newsize: Int
                if (granularity == 0) {    // this is a hack to fix our memset classes
                    granularity = 16
                }
                newsize = size + granularity
                Resize(newsize - newsize % granularity)
            }
            if (index < 0) {
                index = 0
            } else if (index > num) {
                index = num
            }
            for (i in num downTo index + 1) {
                list.get(i) = list.get(i - 1)
            }
            num++
            list.get(index) = obj
            return index
        }

        /*
         ================
         idList<type>::FindIndex

         Searches for the specified data in the list and returns it's index.  Returns -1 if the data is not found.
         ================
         */
        fun FindIndex(obj: type?): Int {                // find the index for the given element
            var i: Int
            i = 0
            while (i < num) {
                if (list.get(i) == obj) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        /*
         ================
         idList<type>::Find

         Searches for the specified data in the list and returns it's address. Returns NULL if the data is not found.
         ================
         */
        fun Find(obj: type?): Int? {                        // find pointer to the given element
            val i: Int
            i = FindIndex(obj)
            return if (i >= 0) {
                i //TODO:test whether returning the index instead of the address works!!!
            } else null
        }

        /*
         ================
         idList<type>::FindNull

         Searches for a NULL pointer in the list.  Returns -1 if NULL is not found.

         NOTE: This function can only be called on lists containing pointers. Calling it
         on non-pointer lists will cause a compiler error.
         ================
         */
        fun FindNull(): Int {                                // find the index for the first NULL pointer in the list
            var i: Int
            i = 0
            while (i < num) {
                if (TempDump.NOT(list.get(i))) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        /*
         ================
         idList<type>::IndexOf

         Takes a pointer to an element in the list and returns the index of the element.
         This is NOT a guarantee that the object is really in the list.
         Function will assert in debug builds if pointer is outside the bounds of the list,
         but remains silent in release builds.
         ================
         */
        fun IndexOf(objptr: type?): Int {                    // returns the index for the pointer to an element in the list
            val index: Int

//            index = objptr - list;
            index = FindIndex(objptr)
            assert(index >= 0)
            assert(index < num)
            return index
        }

        /*
         ================
         idList<type>::RemoveIndex

         Removes the element at the specified index and moves all data following the element down to fill in the gap.
         The number of elements in the list is reduced by one.  Returns false if the index is outside the bounds of the list.
         Note that the element is not destroyed, so any memory used by it may not be freed until the destruction of the list.
         ================
         */
        fun RemoveIndex(index: Int): Boolean {                            // remove the element at the given index
            var i: Int
            assert(list != null)
            assert(index >= 0)
            assert(index < num)
            if (index < 0 || index >= num) {
                return false
            }
            num--
            i = index
            while (i < num) {
                list.get(i) = list.get(i + 1)
                i++
            }
            return true
        }

        /*
         ================
         idList<type>::Remove

         Removes the element if it is found within the list and moves all data following the element down to fill in the gap.
         The number of elements in the list is reduced by one.  Returns false if the data is not found in the list.  Note that
         the element is not destroyed, so any memory used by it may not be freed until the destruction of the list.
         ================
         */
        fun Remove(obj: type?): Boolean {                            // remove the element
            val index: Int
            index = FindIndex(obj)
            return if (index >= 0) {
                RemoveIndex(index)
            } else false
        }

        /*
         ================
         idList<type>::Sort

         Performs a qsort on the list using the supplied comparison function.  Note that the data is merely moved around the
         list, so any pointers to data within the list may no longer be valid.
         ================
         */
        fun Sort() {
            if (TempDump.NOT(*list)) {
                return
            }
            if (list.get(0) is idPoolStr) {
                this.Sort(object : cmp_t<idStr?> {
                    override fun compare(a: idStr?, b: idStr?): Int {
                        return a.Icmp(b)
                    }
                })
            } else if (list.get(0) is idInternalCVar) {
                this.Sort(CVarSystem.idListSortCompare())
            } else if (list.get(0) is commandDef_s) {
                this.Sort(CmdSystem.idListSortCompare())
            } else {
                this.Sort(neo.idlib.containers.List.idListSortCompare<type?>())
            }
        }

        fun Sort(compare: cmp_t<*>? /*= ( cmp_t * )&idListSortCompare<type> */) {

//	typedef int cmp_c(const void *, const void *);
//
//	cmp_c *vCompare = (cmp_c *)compare;
//	qsort( ( void * )list, ( size_t )num, sizeof( type ), vCompare );
            if (list != null) {
                Arrays.sort(list, compare)
            }
        }

        /*
         ================
         idList<type>::SortSubSection

         Sorts a subsection of the list.
         ================
         */
        @JvmOverloads
        fun SortSubSection(
            startIndex: Int,
            endIndex: Int,
            compare: cmp_t<*>? = neo.idlib.containers.List.idListSortCompare<type?>() /*= ( cmp_t * )&idListSortCompare<type>*/
        ) {
            var startIndex = startIndex
            var endIndex = endIndex
            if (TempDump.NOT(*list)) {
                return
            }
            if (startIndex < 0) {
                startIndex = 0
            }
            if (endIndex >= num) {
                endIndex = num - 1
            }
            if (startIndex >= endIndex) {
                return
            }
            //	typedef int cmp_c(const void *, const void *);
//
//	cmp_c *vCompare = (cmp_c *)compare;
//	qsort( ( void * )( &list[startIndex] ), ( size_t )( endIndex - startIndex + 1 ), sizeof( type ), vCompare );
            Arrays.sort(list, startIndex, endIndex, compare)
        }

        /*
         ================
         idList<type>::Swap

         Swaps the contents of two lists
         ================
         */
        fun Swap(other: idList<type?>?) {                        // swap the contents of the lists
            val swap_num: Int
            val swap_size: Int
            val swap_granularity: Int
            val swap_list: Array<type?>?
            swap_num = num
            swap_size = size
            swap_granularity = granularity
            swap_list = list
            num = other.num
            size = other.size
            granularity = other.granularity
            list = other.list
            other.num = swap_num
            other.size = swap_size
            other.granularity = swap_granularity
            other.list = swap_list
        }

        /*
         ================
         idList<type>::DeleteContents

         Calls the destructor of all elements in the list.  Conditionally frees up memory used by the list.
         Note that this only works on lists containing pointers to objects and will cause a compiler error
         if called with non-pointers.  Since the list was not responsible for allocating the object, it has
         no information on whether the object still exists or not, so care must be taken to ensure that
         the pointers are still valid when this function is called.  Function will set all pointers in the
         list to NULL.
         ================
         */
        fun DeleteContents(clear: Boolean) {                        // delete the contents of the list
            var i: Int
            i = 0
            while (i < num) {

//		delete list[i ];
                list.get(i) = null
                i++
            }
            if (clear) {
                Clear()
            } else {
//		memset( list, 0, size * sizeof( type ) );
                list = arrayOfNulls<Any?>(list.size) as Array<type?>
            }
        }

        companion object {
            //TODO: implement java.util.List
            const val SIZE = (Integer.SIZE
                    + Integer.SIZE
                    + Integer.SIZE
                    + TempDump.CPP_class.Pointer.SIZE) //type

            //
            private var DBG_counter = 0
        }
    }

    private class idListSortCompare<type> : cmp_t<type?> {
        override fun compare(a: type?, b: type?): Int {
            return reflects._Minus(a, b)
        }
    }
}