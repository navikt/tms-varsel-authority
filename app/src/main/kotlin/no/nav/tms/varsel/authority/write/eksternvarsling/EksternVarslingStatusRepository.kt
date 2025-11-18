package no.nav.tms.varsel.authority.write.eksternvarsling

import kotliquery.queryOf
import no.nav.tms.varsel.authority.EksternVarslingStatus
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.toJsonb
import no.nav.tms.varsel.authority.config.defaultObjectMapper

class EksternVarslingStatusRepository(val database: Database) {
    private val objectMapper = defaultObjectMapper()

    fun updateEksternVarslingStatus(varselId: String, eksternVarslingStatus: EksternVarslingStatus) {
        database.update {
            queryOf(
                "update varsel set eksternVarslingStatus = :status where varselId = :varselId",
                mapOf("varselId" to varselId, "status" to eksternVarslingStatus.toJsonb(objectMapper))
            )
        }
    }
}
