package no.nav.tms.varsel.authority.write.arkiv

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.json
import no.nav.tms.varsel.authority.config.defaultObjectMapper

class ArchiveTestRepository(private val database: Database) {
    private val objectMapper = defaultObjectMapper()

    fun getAllArchivedVarsel(): List<ArkivVarsel> {
        return database.list {
            queryOf("select * from varsel_arkiv")
                .map(toArchiveVarsel())
                .asList
        }
    }

    private fun toArchiveVarsel(): (Row) -> ArkivVarsel = { row ->
        row.json("varsel", objectMapper)
    }
}
