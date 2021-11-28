package neo.idlib.containers

class CBool {
    private var `val` = false

    constructor()
    constructor(`val`: Boolean) {
        this.`val` = `val`
    }

    fun isVal(): Boolean {
        return `val`
    }

    fun setVal(`val`: Boolean) {
        this.`val` = `val`
    }
}