package no.nav.tms.varsel.authority.archive

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.database.Database
import no.nav.tms.varsel.authority.common.database.json
import no.nav.tms.varsel.authority.config.defaultObjectMapper

class ArchiveTestRepository(private val database: Database) {
    private val objectMapper = defaultObjectMapper()

    fun getAllArchivedVarsel(): List<ArchiveVarsel> {
        return database.list {
            queryOf("select * from varsel_archive")
                .map(toArchiveVarsel())
                .asList
        }
    }

    private fun toArchiveVarsel(): (Row) -> ArchiveVarsel = { row ->
        row.json("varsel", objectMapper)
    }
}
