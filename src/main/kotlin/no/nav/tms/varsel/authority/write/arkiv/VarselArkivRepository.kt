package no.nav.tms.varsel.authority.write.arkiv

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.json
import no.nav.tms.varsel.authority.common.optionalJson
import no.nav.tms.varsel.authority.common.toJsonb
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import java.time.ZonedDateTime

class VarselArkivRepository(private val database: Database) {

    private val objectMapper = defaultObjectMapper()

    fun archiveOldVarsler(dateThreshold: ZonedDateTime): List<ArkivVarsel> {

        val archivableVarsler = getVarslerOlderThanThreshold(dateThreshold)

        if (archivableVarsler.isNotEmpty()) {
            insertArkivVarsler(archivableVarsler)
            deleteVarsler(archivableVarsler.map { it.varselId })
        }

        return archivableVarsler
    }

    private fun getVarslerOlderThanThreshold(dateThreshold: ZonedDateTime): List<ArkivVarsel> {
        return database.list {
            queryOf(
                "select * from varsel where opprettet < :threshold",
                mapOf("threshold" to dateThreshold)
            ).map(toArchiveVarsel())
            .asList
        }
    }

    private fun insertArkivVarsler(varsler: List<ArkivVarsel>) {
        database.batch(
            """
                insert into varsel_arkiv(varselId, ident, varsel, arkivert)
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

    private fun toArchiveVarsel(): (Row) -> ArkivVarsel = { row ->
        ArkivVarsel(
            type = row.string("type").let(VarselType::parse),
            varselId = row.string("varselId"),
            ident = row.string("ident"),
            aktiv = row.boolean("aktiv"),
            sensitivitet = row.string("sensitivitet").let(Sensitivitet::parse),
            innhold = row.json("innhold"),
            produsent = row.json("produsent"),
            eksternVarslingBestilling = row.optionalJson("eksternVarslingBestilling", objectMapper),
            eksternVarslingStatus = row.optionalJson("eksternVarslingStatus", objectMapper),
            opprettet = row.zonedDateTime("opprettet"),
            inaktivert = row.zonedDateTimeOrNull("inaktivert"),
            inaktivertAv = row.stringOrNull("inaktivertAv")?.let { VarselInaktivertKilde.from(it) }
        )
    }
}

data class ArkivVarsel(
    val type: VarselType,
    val varselId: String,
    val ident: String,
    val aktiv: Boolean,
    val sensitivitet: Sensitivitet,
    val innhold: Innhold,
    val produsent: Produsent,
    val eksternVarslingBestilling: EksternVarslingBestilling? = null,
    val eksternVarslingStatus: EksternVarslingStatus? = null,
    val opprettet: ZonedDateTime,
    val inaktivert: ZonedDateTime? = null,
    val inaktivertAv: VarselInaktivertKilde? = null
)
