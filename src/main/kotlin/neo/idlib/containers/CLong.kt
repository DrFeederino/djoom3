package neo.idlib.containers

class CLong {
    private var `val`: Long = 0

    constructor()
    constructor(`val`: Long) {
        this.`val` = `val`
    }

    fun getVal(): Long {
        return `val`
    }

    fun setVal(`val`: Long) {
        this.`val` = `val`
    }
}