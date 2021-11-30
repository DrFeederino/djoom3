package neo.idlib.containers

import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 *
 */
class StaticList {
    /*
     ===============================================================================

     Static list template
     A non-growing, memset-able list using no memory allocation.

     ===============================================================================
     */
    class idStaticList<type>(private val size: Int) {
        private var list: Array<type?>?
        private var num = 0
        private var type: Class<type?>? = null

        constructor(size: Int, type: Class<type?>?) : this(size) {
            this.type = type
        }

        constructor(size: Int, `object`: Any?) : this(size) {
            list = (`object` as idStaticList<type?>?).list
        }

        //	public					idStaticList( const idStaticList<type,size> &other );
        //	public					~idStaticList<type,size>( void );
        //
        /*
         ================
         idStaticList<type,size>::Clear

         Sets the number of elements in the list to 0.  Assumes that type automatically handles freeing up memory.
         ================
         */
        fun Clear() {                                        // marks the list as empty.  does not deallocate or intialize data.
            num = 0
        }

        /*
         ================
         idStaticList<type,size>::Num

         Returns the number of elements currently contained in the list.
         ================
         */
        fun Num(): Int {                                    // returns number of elements in list
            return num
        }

        /*
         ================
         idStaticList<type,size>::Max

         Returns the maximum number of elements in the list.
         ================
         */
        fun Max(): Int {                                    // returns the maximum number of elements in the list
            return size
        }

        /*
         ================
         idStaticList<type,size>::SetNum

         Set number of elements in list.
         ================
         */
        fun SetNum(newnum: Int) {                                // set number of elements in list
            assert(newnum >= 0)
            assert(newnum <= size)
            num = newnum
        }

        //
        //public		size_t				Allocated( void ) const;							// returns total size of allocated memory
        //public		size_t				Size( void ) const;									// returns total size of allocated memory including size of list type
        // returns size of the used elements in the list
        fun  /*size_t*/MemoryUsed(): Int {
            return num * Integer.BYTES //TODO: * sizeof(list[0]);
        }

        //
        /*
         ================
         idStaticList<type,size>::operator[] const

         Access operator.  Index must be within range or an assert will be issued in debug builds.
         Release builds do no range checking.
         ================
         */
        fun oGet(index: Int): type? {
            assert(index >= 0)
            assert(index < num)
            return list.get(index)
        }

        fun oSet(index: Int, value: type?): type? {
            assert(index >= 0)
            assert(index < num)
            return value.also { list.get(index) = it }
        }

        //public		type &				operator[]( int index );
        //
        /*
         ================
         idStaticList<type,size>::Ptr

         Returns a pointer to the begining of the array.  Useful for iterating through the list in loops.

         Note: may return NULL if the list is empty.

         FIXME: Create an iterator template for this kind of thing.
         ================
         */
        fun Ptr(): Array<type?>? {                                        // returns a pointer to the list
            return list
        }

        //public		const type *		Ptr( void ) const;									// returns a pointer to the list
        /*
         ================
         idStaticList<type,size>::Alloc

         Returns a pointer to a new data element at the end of the list.
         ================
         */
        fun Alloc(): type? {                                        // returns reference to a new data element at the end of the list.  returns NULL when full.
            if (num >= size) {
                return null
            }
            try {
                return type.newInstance().also {
                    list.get(num++) = it //TODO:init value before sending back. EDIT:ugly, but working.
                }
            } catch (ex: InstantiationException) {
                Logger.getLogger(StaticList::class.java.name).log(Level.SEVERE, null, ex)
            } catch (ex: IllegalAccessException) {
                Logger.getLogger(StaticList::class.java.name).log(Level.SEVERE, null, ex)
            }
            return null
        }

        /*
         ================
         idStaticList<type,size>::Append

         Increases the size of the list by one element and copies the supplied data into it.

         Returns the index of the new element, or -1 when list is full.
         ================
         */
        fun Append(obj: type?): Int { // append element
            assert(num < size)
            if (num < size) {
                list.get(num) = obj
                num++
                return num - 1
            }
            return -1
        }

        /*
         ================
         idStaticList<type,size>::Append

         adds the other list to this one

         Returns the size of the new combined list
         ================
         */
        fun Append(other: idStaticList<type?>?): Int {        // append list
            var i: Int
            var n = other.Num()
            if (num + n > other.size) { //TODO:which size??
                n = size - num
            }
            i = 0
            while (i < n) {
                list.get(i + num) = other.list.get(i)
                i++
            }
            num += n
            return Num()
        }

        /*
         ================
         idStaticList<type,size>::AddUnique

         Adds the data to the list if it doesn't already exist.  Returns the index of the data in the list.
         ================
         */
        fun AddUnique(obj: type?): Int {                        // add unique element
            var index: Int
            index = FindIndex(obj)
            if (index < 0) {
                index = Append(obj)
            }
            return index
        }

        /*
         ================
         idStaticList<type,size>::Insert

         Increases the size of the list by at leat one element if necessary 
         and inserts the supplied data into it.

         Returns the index of the new element, or -1 when list is full.
         ================
         */
        fun Insert(obj: type?, index: Int): Int {                // insert the element at the given index
            var index = index
            var i: Int
            assert(num < size)
            if (num >= size) {
                return -1
            }
            assert(index >= 0)
            if (index < 0) {
                index = 0
            } else if (index > num) {
                index = num
            }
            i = num
            while (i > index) {
                list.get(i) = list.get(i - 1)
                --i
            }
            num++
            list.get(index) = obj
            return index
        }

        /*
         ================
         idStaticList<type,size>::FindIndex

         Searches for the specified data in the list and returns it's index.  Returns -1 if the data is not found.
         ================
         */
        fun FindIndex(obj: type?): Int {                // find the index for the given element
            var i: Int
            i = 0
            while (i < num) {
                if (list.get(i) === obj) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        //public		type *				Find( type const & obj ) const;						// find pointer to the given element
        /*
         ================
         idStaticList<type,size>::FindNull

         Searches for a NULL pointer in the list.  Returns -1 if NULL is not found.

         NOTE: This function can only be called on lists containing pointers. Calling it
         on non-pointer lists will cause a compiler error.
         ================
         */
        fun FindNull(): Int {                                // find the index for the first NULL pointer in the list
            var i: Int
            i = 0
            while (i < num) {
                if (list.get(i) == null) {
                    return i
                }
                i++
            }

            // Not found
            return -1
        }

        /*
         ================
         idStaticList<type,size>::IndexOf

         Takes a pointer to an element in the list and returns the index of the element.
         This is NOT a guarantee that the object is really in the list. 
         Function will assert in debug builds if pointer is outside the bounds of the list,
         but remains silent in release builds.
         ================
         */
        fun IndexOf(obj: type?): Int {                    // returns the index for the pointer to an element in the list
//    int index;
//
//	index = objptr - list;
//
//	assert( index >= 0 );
//	assert( index < num );
//
//	return index;
            return FindIndex(obj)
        }

        /*
         ================
         idStaticList<type,size>::RemoveIndex

         Removes the element at the specified index and moves all data following the element down to fill in the gap.
         The number of elements in the list is reduced by one.  Returns false if the index is outside the bounds of the list.
         Note that the element is not destroyed, so any memory used by it may not be freed until the destruction of the list.
         ================
         */
        fun RemoveIndex(index: Int): Boolean {                            // remove the element at the given index
            var i: Int
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
         idStaticList<type,size>::Remove

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

        //public		void				Swap( idStaticList<type,size> &other );				// swap the contents of the lists
        /*
         ================
         idStaticList<type,size>::DeleteContents

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
            while (i < size) {

//		delete list[ i ];
                list.get(i) = null
                i++
            }
            if (clear) {
                Clear()
            } else {
//		memset( list, 0, sizeof( list ) );
                Arrays.fill(list, 0)
            }
        }

        //
        //
        init {
            list = arrayOfNulls<Any?>(size) as Array<type?>
        }
    }
}