package neo.idlib.containers

class CInt {
    var _val = 0

    constructor()
    constructor(out: Int) {
        this._val = out
    }

    fun increment(): Int {
        return _val++
    }

    fun decrement(): Int {
        return _val--
    }

    fun rightShift(power: Int) {
        _val = _val shr power
    }

    fun leftShit(power: Int) {
        _val = _val shl power
    }
}