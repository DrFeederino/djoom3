package neo.idlib.math.Matrix

import neo.idlib.math.Vector.idVec3
import java.util.stream.Stream

/**
 * Yes, the one, the only, the ever illusive zero matrix.
 */
object idMat0 {
    const val MATRIX_EPSILON = 1.0E-6 //TODO: re-type to float.
    const val MATRIX_INVERSE_EPSILON = 1.0E-14
    fun matrixPrint(x: idMatX?, label: String?) {
        val rows = x.GetNumRows()
        val columns = x.GetNumColumns()
        println("START $label")
        for (b in 0 until rows) {
            for (a in 0 until columns) {
                print(x.oGet(b, a).toString() + "\t")
            }
            println()
        }
        println("STOP $label")
    }

    fun genVec3Array(size: Int): Array<idVec3?>? {
        return Stream.generate { idVec3() }.limit(size.toLong()).toArray { _Dummy_.__Array__() }
    }
}