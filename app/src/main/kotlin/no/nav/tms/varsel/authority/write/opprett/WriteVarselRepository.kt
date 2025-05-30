package no.nav.tms.varsel.authority.write.opprett

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.common.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde

class WriteVarselRepository(val database: Database) {

    private val objectMapper = defaultObjectMapper()

    fun insertVarsel(dbVarsel: DatabaseVarsel) {

        database.insert {
            queryOf(
                """
                    insert into varsel(
                        type,
                        varselId,
                        ident,
                        sensitivitet,
                        innhold,
                        produsent,
                        eksternVarslingBestilling,
                        eksternVarslingStatus,
                        aktiv,
                        opprettet,
                        aktivFremTil,
                        inaktivert,
                        inaktivertAv,
                        metadata
                    ) values (
                        :type,
                        :varselId,
                        :ident,
                        :sensitivitet,
                        :innhold,
                        :produsent,
                        :eksternVarslingBestilling,
                        :eksternVarslingStatus,
                        :aktiv,
                        :opprettet,
                        :aktivFremTil,
                        :inaktivert,
                        :inaktivertAv,
                        :metadata
                    )
                """,
                mapOf(
                    "type" to dbVarsel.type.name.lowercase(),
                    "varselId" to dbVarsel.varselId,
                    "ident" to dbVarsel.ident,
                    "sensitivitet" to dbVarsel.sensitivitet.name.lowercase(),
                    "innhold" to dbVarsel.innhold.toJsonb(objectMapper),
                    "produsent" to dbVarsel.produsent.toJsonb(objectMapper),
                    "eksternVarslingBestilling" to dbVarsel.eksternVarslingBestilling.toJsonb(objectMapper),
                    "eksternVarslingStatus" to dbVarsel.eksternVarslingStatus.toJsonb(objectMapper),
                    "aktiv" to dbVarsel.aktiv,
                    "opprettet" to dbVarsel.opprettet,
                    "aktivFremTil" to dbVarsel.aktivFremTil,
                    "inaktivert" to dbVarsel.inaktivert,
                    "inaktivertAv" to dbVarsel.inaktivertAv,
                    "metadata" to dbVarsel.metadata.toJsonb(objectMapper)
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

    fun inaktiverVarsel(varselId: String, kilde: VarselInaktivertKilde, metadata: Map<String, Any>? = null) {
        database.update {
            queryOf(
                """
                    update varsel set 
                      aktiv = false,
                      inaktivertAv = :kilde,
                      inaktivert = :tidspunkt,
                      metadata = coalesce(metadata::jsonb, '{}'::jsonb) || coalesce(:metadata, '{}'::jsonb)
                    where varselId = :varselId
                """,
                mapOf(
                    "varselId" to varselId,
                    "kilde" to kilde.lowercaseName,
                    "tidspunkt" to nowAtUtc(),
                    "metadata" to metadata.toJsonb(objectMapper)
                )
            )
        }
    }

    private fun toDbVarsel(): (Row) -> DatabaseVarsel = { row ->
        val varselInnhold: Innhold = row.json("innhold", objectMapper)

        DatabaseVarsel(
            type = row.string("type").let(::parseVarseltype),
            varselId = row.string("varselId"),
            ident = row.string("ident"),
            aktiv = row.boolean("aktiv"),
            sensitivitet = row.string("sensitivitet").let(::parseSensitivitet),
            innhold = varselInnhold,
            produsent = row.json("produsent"),
            eksternVarslingBestilling = row.optionalJson("eksternVarslingBestilling", objectMapper),
            eksternVarslingStatus = row.optionalJson("eksternVarslingStatus", objectMapper),
            opprettet = row.zonedDateTime("opprettet"),
            inaktivert = row.zonedDateTimeOrNull("inaktivert"),
            inaktivertAv = row.stringOrNull("inaktivertAv")?.let { VarselInaktivertKilde.from(it) },
            aktivFremTil = row.zonedDateTimeOrNull("aktivFremTil"),
            metadata = row.optionalJson("metadata")
        )
    }
}
