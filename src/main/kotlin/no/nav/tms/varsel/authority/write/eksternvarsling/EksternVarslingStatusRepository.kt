package no.nav.tms.varsel.authority.write.eksternvarsling

import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.database.Database
import no.nav.tms.varsel.authority.common.database.toJsonb
import no.nav.tms.varsel.authority.write.sink.EksternVarslingStatus

class EksternVarslingStatusRepository(val database: Database) {
    fun updateEksternVarslingStatus(varselId: String, eksternVarslingStatus: EksternVarslingStatus) {
        database.update {
            queryOf(
                "update varsel set eksternVarslingStatus = :status where varselId = :varselId",
                mapOf("varselId" to varselId, "status" to eksternVarslingStatus.toJsonb())
            )
        }
    }
}
