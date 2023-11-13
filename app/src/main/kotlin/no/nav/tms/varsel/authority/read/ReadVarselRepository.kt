package no.nav.tms.varsel.authority.read

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.*
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.action.Varseltype

class ReadVarselRepository(private val database: Database) {
    private val objectMapper = defaultObjectMapper()

    fun getVarselSammendragForUser(ident: String, type: Varseltype? = null, aktiv: Boolean? = null, spraakkode: String? = null): List<DatabaseVarselsammendrag> {
        return database.list {
            queryOf("""
                select
                  varselId,
                  type,
                  aktiv,
                  innhold,
                  sensitivitet,
                  eksternVarslingStatus -> 'sendt' as eksternVarslingSendt,
                  eksternVarslingStatus -> 'kanaler' as eksternVarslingKanaler,
                  opprettet,
                  aktivFremTil,
                  inaktivert,
                  inaktivertAv
                from varsel where ident = :ident
                    ${ if (type != null) " and type = :type " else "" }
                    ${ if (aktiv != null) " and aktiv = :aktiv " else "" }
            """,
                mapOf("ident" to ident, "type" to type?.name?.lowercase(), "aktiv" to aktiv)
            )
                .map(toVarselsammendrag())
                .asList
        }
    }

    fun getDetaljertVarselForUser(ident: String, type: Varseltype? = null, aktiv: Boolean? = null): List<DetaljertVarsel> {
        return database.list {
            queryOf("""
                select
                  varselId,
                  type,
                  aktiv,
                  produsent,
                  innhold,
                  sensitivitet,
                  eksternVarslingStatus,
                  opprettet,
                  aktivFremTil,
                  inaktivert,
                  inaktivertAv
                from varsel where ident = :ident
                    ${ if (type != null) " and type = :type " else "" }
                    ${ if (aktiv != null) " and aktiv = :aktiv " else "" }
            """,
                mapOf("ident" to ident, "type" to type?.name?.lowercase(), "aktiv" to aktiv)
            )
                .map(toDetaljertVarsel())
                .asList
        }
    }

    private fun toVarselsammendrag(): (Row) -> DatabaseVarselsammendrag = {
        DatabaseVarselsammendrag(
            type = it.string("type").let(::parseVarseltype),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            innhold = it.json("innhold", objectMapper),
            sensitivitet = it.string("sensitivitet").let(::parseSensitivitet),
            eksternVarslingSendt = it.boolean("eksternVarslingSendt"),
            eksternVarslingKanaler = it.optionalJson("eksternVarslingKanaler", objectMapper) ?: emptyList(),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTimeOrNull("inaktivert")
        )
    }

    private fun toDetaljertVarsel(): (Row) -> DetaljertVarsel = {
        DetaljertVarsel(
            type = it.string("type").let(::parseVarseltype),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            produsent = it.json("produsent", objectMapper),
            innhold = it.json("innhold", objectMapper),
            sensitivitet = it.string("sensitivitet").let(::parseSensitivitet),
            eksternVarsling = it.optionalJson("eksternVarslingStatus", objectMapper),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTimeOrNull("inaktivert"),
            inaktivertAv = it.stringOrNull("inaktivertAv")
        )
    }
}
