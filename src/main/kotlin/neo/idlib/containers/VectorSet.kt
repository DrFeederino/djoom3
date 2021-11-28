package neo.idlib.containers

import neo.framework.DeclAF.idAFVector.type
import neo.idlib.containers.HashIndex.idHashIndex
import neo.idlib.containers.List.idList
import neo.idlib.math.Math_h.idMath
import neo.idlib.math.Vector.idVec

/**
 *
 */
class VectorSet {
    /*
     ===============================================================================

     Vector Set

     Creates a set of vectors without duplicates.

     ===============================================================================
     */
    class idVectorSet<type> : idList<type?> {
        //
        //
        private val dimension: Int
        private var boxHalfSize /*= new float[dimension]*/: FloatArray?
        private var boxHashSize = 0
        private var boxInvSize /*= new float[dimension]*/: FloatArray?
        private val hash: idHashIndex? = null
        private var maxs: idVec<*>? = null
        private var mins: idVec<*>? = null

        //
        //
        constructor(dimension: Int) {
            this.dimension = dimension
            boxInvSize = FloatArray(dimension)
            boxHalfSize = FloatArray(dimension)
            hash.Clear(idMath.IPow(boxHashSize, dimension), 128)
            boxHashSize = 16
            //	memset( boxInvSize, 0, dimension * sizeof( boxInvSize[0] ) );
//	memset( boxHalfSize, 0, dimension * sizeof( boxHalfSize[0] ) );
        }

        constructor(mins: idVec<*>?, maxs: idVec<*>?, boxHashSize: Int, initialSize: Int, dimension: Int) {
            this.dimension = dimension
            Init(mins, maxs, boxHashSize, initialSize)
        }

        //
        //							// returns total size of allocated memory
        //public	size_t					Allocated( void ) const { return idList<type>::Allocated() + hash.Allocated(); }
        //							// returns total size of allocated memory including size of type
        //public	size_t					Size( void ) const { return sizeof( *this ) + Allocated(); }
        //
        fun Init(mins: idVec<*>?, maxs: idVec<*>?, boxHashSize: Int, initialSize: Int) {
            var i: Int
            var boxSize: Float
            super.AssureSize(initialSize)
            super.SetNum(0, false)
            hash.Clear(idMath.IPow(boxHashSize, dimension), initialSize)
            this.mins = mins
            this.maxs = maxs
            this.boxHashSize = boxHashSize
            i = 0
            while (i < dimension) {
                boxSize = (maxs.oGet(i) - mins.oGet(i)) / boxHashSize.toFloat()
                boxInvSize.get(i) = 1.0f / boxSize
                boxHalfSize.get(i) = boxSize * 0.5f
                i++
            }
        }

        fun ResizeIndex(newSize: Int) {
            super.Resize(newSize)
            hash.ResizeIndex(newSize)
        }

        override fun Clear() {
            super.Clear()
            hash.Clear()
        }

        //
        fun FindVector(v: idVec<*>?, epsilon: Float): Int {
            var i: Int
            var j: Int
            var k: Int
            var hashKey: Int
            val partialHashKey = IntArray(dimension)
            i = 0
            while (i < dimension) {
                assert(epsilon <= boxHalfSize.get(i))
                partialHashKey[i] = ((v.oGet(i) - mins.oGet(i) - boxHalfSize.get(i)) * boxInvSize.get(i)).toInt()
                i++
            }
            i = 0
            while (i < 1 shl dimension) {
                hashKey = 0
                j = 0
                while (j < dimension) {
                    hashKey *= boxHashSize
                    hashKey += partialHashKey[j] + (i shr j and 1)
                    j++
                }
                j = hash.First(hashKey)
                while (j >= 0) {
                    val lv = oGet(j) as idVec<*>?
                    k = 0
                    while (k < dimension) {
                        if (Math.abs(lv.oGet(k) - v.oGet(k)) > epsilon) {
                            break
                        }
                        k++
                    }
                    if (k >= dimension) {
                        return j
                    }
                    j = hash.Next(j)
                }
                i++
            }
            hashKey = 0
            i = 0
            while (i < dimension) {
                hashKey *= boxHashSize
                hashKey += ((v.oGet(i) - mins.oGet(i)) * boxInvSize.get(i)).toInt()
                i++
            }
            hash.Add(hashKey, super.Num())
            this.Append(v as type?)
            return super.Num() - 1
        }
    }

    /*
     ===============================================================================

     Vector Subset

     Creates a subset without duplicates from an existing list with vectors.

     ===============================================================================
     */
    class idVectorSubset<type> {
        //
        private val dimension: Int
        private var boxHalfSize /*= new float[dimension]*/: FloatArray?
        private var boxHashSize = 0
        private var boxInvSize /*= new float[dimension]*/: FloatArray?
        private val hash: idHashIndex? = idHashIndex()
        private var maxs: idVec<*>? = null
        private var mins: idVec<*>? = null

        //
        //
        private constructor() {
            dimension = -1
        }

        constructor(dimension: Int) {
            this.dimension = dimension
            boxInvSize = FloatArray(dimension)
            boxHalfSize = FloatArray(dimension)
            hash.Clear(idMath.IPow(boxHashSize, dimension), 128)
            boxHashSize = 16
            //	memset( boxInvSize, 0, dimension * sizeof( boxInvSize[0] ) );
//	memset( boxHalfSize, 0, dimension * sizeof( boxHalfSize[0] ) );
        }

        constructor(mins: idVec<*>?, maxs: idVec<*>?, boxHashSize: Int, initialSize: Int, dimension: Int) {
            this.dimension = dimension
            Init(mins, maxs, boxHashSize, initialSize)
        }

        //
        //							// returns total size of allocated memory
        //	size_t					Allocated( void ) const { return idList<type>::Allocated() + hash.Allocated(); }
        //							// returns total size of allocated memory including size of type
        //	size_t					Size( void ) const { return sizeof( *this ) + Allocated(); }
        //
        fun Init(mins: idVec<*>?, maxs: idVec<*>?, boxHashSize: Int, initialSize: Int) {
            var i: Int
            var boxSize: Float
            hash.Clear(idMath.IPow(boxHashSize, dimension), initialSize)
            this.mins = mins
            this.maxs = maxs
            this.boxHashSize = boxHashSize
            i = 0
            while (i < dimension) {
                boxSize = (maxs.oGet(i) - mins.oGet(i)) / boxHashSize.toFloat()
                boxInvSize.get(i) = 1.0f / boxSize
                boxHalfSize.get(i) = boxSize * 0.5f
                i++
            }
        }

        fun Clear() {
//	idList<type>::Clear();
            hash.Clear()
        }

        //
        // returns either vectorNum or an index to a previously found vector
        fun FindVector(vectorList: Array<idVec<*>?>?, vectorNum: Int, epsilon: Float): Int {
            var i: Int
            var j: Int
            var k: Int
            var hashKey: Int
            val partialHashKey = IntArray(dimension)
            val v = vectorList.get(vectorNum)
            i = 0
            while (i < dimension) {
                assert(epsilon <= boxHalfSize.get(i))
                partialHashKey[i] = ((v.oGet(i) - mins.oGet(i) - boxHalfSize.get(i)) * boxInvSize.get(i)).toInt()
                i++
            }
            i = 0
            while (i < 1 shl dimension) {
                hashKey = 0
                j = 0
                while (j < dimension) {
                    hashKey *= boxHashSize
                    hashKey += partialHashKey[j] + (i shr j and 1)
                    j++
                }
                j = hash.First(hashKey)
                while (j >= 0) {
                    val lv = vectorList.get(j)
                    k = 0
                    while (k < dimension) {
                        if (Math.abs(lv.oGet(k) - v.oGet(k)) > epsilon) {
                            break
                        }
                        k++
                    }
                    if (k >= dimension) {
                        return j
                    }
                    j = hash.Next(j)
                }
                i++
            }
            hashKey = 0
            i = 0
            while (i < dimension) {
                hashKey *= boxHashSize
                hashKey += ((v.oGet(i) - mins.oGet(i)) * boxInvSize.get(i)).toInt()
                i++
            }
            hash.Add(hashKey, vectorNum)
            return vectorNum
        }
    }
}