package no.nav.tms.varsel.authority.write.archive

import com.fasterxml.jackson.annotation.JsonIgnore
import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.json
import no.nav.tms.varsel.authority.common.optionalJson
import no.nav.tms.varsel.authority.common.toJsonb
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import no.nav.tms.varsel.authority.EksternVarslingBestilling
import no.nav.tms.varsel.authority.EksternVarslingStatus
import no.nav.tms.varsel.authority.Produsent
import no.nav.tms.varsel.authority.Varsel
import java.time.ZonedDateTime

class VarselArchiveRepository(private val database: Database) {

    private val objectMapper = defaultObjectMapper()

    fun archiveOldVarsler(dateThreshold: ZonedDateTime): List<ArchiveVarsel> {

        val archivableVarsler = getArchivableVarsler(dateThreshold)

        if (archivableVarsler.isNotEmpty()) {
            insertArchiveVarsler(archivableVarsler)
            deleteVarsler(archivableVarsler.map { it.varselId })
        }

        return archivableVarsler
    }

    private fun getArchivableVarsler(dateThreshold: ZonedDateTime): List<ArchiveVarsel> {
        return database.list {
            queryOf(
                "select * from varsel where opprettet < :threshold",
                mapOf("threshold" to dateThreshold)
            ).map(toArchiveVarsel())
            .asList
        }
    }

    private fun insertArchiveVarsler(varsler: List<ArchiveVarsel>) {
        database.batch(
            """
                insert into varsel_archive(varselId, ident, varsel, arkivert)
                values(:varselId, :ident, :varsel, :arkivert)
                on conflict do nothing
            """,
            varsler.map {
                mapOf(
                    "varselId" to it.varselId,
                    "ident" to it.ident,
                    "varsel" to it.toJsonb(objectMapper),
                    "arkivert" to nowAtUtc(),
                )
            }
        )
    }

    private fun deleteVarsler(varselIds: List<String>) {

        database.update {
            val varselIdArray = it.createArrayOf("VARCHAR", varselIds)

            queryOf(
                "delete from varsel where varselId = any(:varselIds)",
                mapOf("varselIds" to varselIdArray)
            )
        }
    }

    private fun toArchiveVarsel(): (Row) -> ArchiveVarsel = { row ->
        val varselInnhold: Varsel = row.json("innhold")

        ArchiveVarsel(
            aktiv = row.boolean("aktiv"),
            varsel = varselInnhold,
            produsent = row.json("produsent"),
            eksternVarslingBestilling = row.optionalJson("eksternVarslingBestilling", objectMapper),
            eksternVarslingStatus = row.optionalJson("eksternVarslingStatus", objectMapper),
            opprettet = row.zonedDateTime("opprettet"),
            inaktivert = row.zonedDateTimeOrNull("inaktivert"),
            inaktivertAv = row.stringOrNull("inaktivertAv")?.let { VarselInaktivertKilde.from(it) }
        )
    }
}

data class ArchiveVarsel(
    val aktiv: Boolean,
    val varsel: Varsel,
    val produsent: Produsent,
    val eksternVarslingBestilling: EksternVarslingBestilling? = null,
    val eksternVarslingStatus: EksternVarslingStatus? = null,
    val opprettet: ZonedDateTime,
    val inaktivert: ZonedDateTime? = null,
    val inaktivertAv: VarselInaktivertKilde? = null
) {
    @JsonIgnore val varselId = varsel.varselId
    @JsonIgnore val ident = varsel.ident
}
