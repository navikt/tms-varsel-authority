package no.nav.tms.varsel.authority.write.eksternvarsling

import kotliquery.queryOf
import no.nav.tms.common.postgres.JsonbHelper.toJsonb
import no.nav.tms.common.postgres.PostgresDatabase
import no.nav.tms.varsel.authority.EksternVarslingStatus

class EksternVarslingStatusRepository(val database: PostgresDatabase) {

    fun updateEksternVarslingStatus(varselId: String, eksternVarslingStatus: EksternVarslingStatus) {
        database.update {
            queryOf(
                "update varsel set eksternVarslingStatus = :status where varselId = :varselId",
                mapOf("varselId" to varselId, "status" to eksternVarslingStatus.toJsonb())
            )
        }
    }
}
