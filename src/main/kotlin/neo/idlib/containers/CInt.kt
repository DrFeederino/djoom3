package neo.idlib.containers

class CInt {
    private var `val` = 0

    constructor(`val`: Int) {
        this.`val` = `val`
    }

    constructor()

    fun getVal(): Int {
        return `val`
    }

    fun setVal(`val`: Int) {
        this.`val` = `val`
    }

    fun increment(): Int {
        return `val`++
    }

    fun decrement(): Int {
        return `val`--
    }

    fun rightShift(power: Int) {
        `val` = `val` shr power
    }

    fun leftShit(power: Int) {
        `val` = `val` shl power
    }
}