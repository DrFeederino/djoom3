package neo.idlib.containers

// This class represents how floats are treated in C with pointers
class CFloat {
    private var internalValue = 0.0f

    constructor()
    constructor(fromFloat: Float) {
        this.internalValue = fromFloat
    }

    fun getVal(): Float {
        return internalValue
    }

    fun setVal(fromFloat: Float) {
        this.internalValue = fromFloat
    }
}