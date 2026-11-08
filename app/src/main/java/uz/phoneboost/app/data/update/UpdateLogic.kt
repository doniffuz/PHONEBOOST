package uz.phoneboost.app.data.update

/** Yangilanish qarori. Minimal qo'llab-quvvatlanadigan versiyadan past ilova MAJBURIY yangilanadi. */
object UpdateLogic {
    data class Decision(val updateAvailable: Boolean, val force: Boolean, val optional: Boolean, val unsupported: Boolean)

    fun decide(current: Int, latest: Int, minSupported: Int, forceFlag: Boolean): Decision {
        val unsupported = current < minSupported
        val available = latest > current
        val force = available && (forceFlag || unsupported)
        return Decision(available, force, available && !force, unsupported)
    }
}
