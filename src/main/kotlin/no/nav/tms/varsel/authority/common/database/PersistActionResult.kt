package no.nav.tms.varsel.authority.common.database

class PersistActionResult private constructor(
        val entityId: Int, val persistOutcome: PersistOutcome) {

    companion object {
        fun success(entityId: Int): PersistActionResult =
                PersistActionResult(entityId, PersistOutcome.SUCCESS)

        fun failure(reason: PersistOutcome): PersistActionResult =
                PersistActionResult(-1, reason)
    }
}


enum class PersistOutcome {
    SUCCESS, NO_INSERT_OR_UPDATE
}
