package no.nav.tms.varsel.authority.write.aktiver

import kotlinx.coroutines.runBlocking
import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Varsel
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.json
import no.nav.tms.varsel.authority.common.optionalJson
import no.nav.tms.varsel.authority.common.toJsonb
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde

class WriteVarselRepository(val database: Database) {

    private val objectMapper = defaultObjectMapper()

    fun createVarsel(dbVarsel: DatabaseVarsel) = runBlocking {

        database.update {
            queryOf(
                """
                    insert into varsel(
                        varselId,
                        type,
                        ident,
                        innhold,
                        produsent,
                        eksternVarslingBestilling,
                        eksternVarslingStatus,
                        aktiv,
                        opprettet,
                        aktivFremTil,
                        inaktivert
                    ) values (
                        :varselId,
                        :type,
                        :ident,
                        :innhold,
                        :produsent,
                        :eksternVarslingBestilling,
                        :eksternVarslingStatus,
                        :aktiv,
                        :opprettet,
                        :aktivFremTil,
                        :inaktivert
                    )
                """,
                mapOf(
                    "varselId" to dbVarsel.varselId,
                    "type" to dbVarsel.type.lowercaseName,
                    "ident" to dbVarsel.ident,
                    "innhold" to dbVarsel.varsel.toJsonb(),
                    "produsent" to dbVarsel.produsent.toJsonb(),
                    "eksternVarslingBestilling" to dbVarsel.eksternVarslingBestilling.toJsonb(),
                    "eksternVarslingStatus" to dbVarsel.eksternVarslingStatus.toJsonb(),
                    "aktiv" to dbVarsel.aktiv,
                    "opprettet" to dbVarsel.opprettet,
                    "aktivFremTil" to dbVarsel.aktivFremTil,
                    "inaktivert" to dbVarsel.inaktivert
                )
            )
        }
    }

    fun getVarsel(varselId: String): DatabaseVarsel? {
        return database.singleOrNull {
            queryOf(
                "select * from varsel where varselId = :varselId",
                mapOf("varselId" to varselId)
            )
                .map(toDbVarsel())
                .asSingle
        }
    }

    fun inaktiverVarsel(varselId: String, kilde: VarselInaktivertKilde) {
        database.update {
            queryOf(
                "update varsel set aktiv = false, inaktivertAv = :kilde, inaktivert = :tidspunkt where varselId = :varselId",
                mapOf(
                    "varselId" to varselId,
                    "kilde" to kilde.lowercaseName,
                    "tidspunkt" to nowAtUtc()
                )
            )
        }
    }

    private fun toDbVarsel(): (Row) -> DatabaseVarsel = { row ->
        val varselInnhold: Varsel = row.json("innhold", objectMapper)

        DatabaseVarsel(
            aktiv = row.boolean("aktiv"),
            varsel = varselInnhold,
            produsent = row.json("produsent"),
            eksternVarslingBestilling = row.optionalJson("eksternVarslingBestilling", objectMapper),
            eksternVarslingStatus = row.optionalJson("eksternVarslingStatus", objectMapper),
            opprettet = row.zonedDateTime("opprettet"),
            inaktivert = row.zonedDateTimeOrNull("inaktivert"),
            inaktivertAv = row.stringOrNull("inaktivertAv")?.let { VarselInaktivertKilde.from(it) },
            aktivFremTil = row.zonedDateTimeOrNull("aktivFremTil")
        )
    }
}
