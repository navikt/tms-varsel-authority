package no.nav.tms.varsel.authority.read

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.Sensitivitet
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.json
import no.nav.tms.varsel.authority.common.optionalJson
import no.nav.tms.varsel.authority.config.defaultObjectMapper

class ReadVarselRepository(private val database: Database) {
    private val objectMapper = defaultObjectMapper()

    fun getVarselSammendragForUser(ident: String, type: VarselType? = null, aktiv: Boolean? = null): List<Varselsammendrag> {
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
                mapOf("ident" to ident, "type" to type?.lowercaseName, "aktiv" to aktiv)
            )
                .map(toVarselsammendrag())
                .asList
        }
    }

    fun getDetaljertVarselForUser(ident: String, type: VarselType? = null, aktiv: Boolean? = null): List<DetaljertVarsel> {
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
                mapOf("ident" to ident, "type" to type?.lowercaseName, "aktiv" to aktiv)
            )
                .map(toDetaljertVarsel())
                .asList
        }
    }

    private fun toVarselsammendrag(): (Row) -> Varselsammendrag = {
        Varselsammendrag(
            type = it.string("type").let(VarselType::parse),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            innhold = it.json("innhold", objectMapper),
            sensitivitet = it.string("sensitivitet").let(Sensitivitet::parse),
            eksternVarslingSendt = it.boolean("eksternVarslingSendt"),
            eksternVarslingKanaler = it.optionalJson("eksternVarslingKanaler", objectMapper) ?: emptyList(),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTimeOrNull("inaktivert")
        )
    }

    private fun toDetaljertVarsel(): (Row) -> DetaljertVarsel = {
        DetaljertVarsel(
            type = it.string("type").let(VarselType::parse),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            produsent = it.json("produsent", objectMapper),
            innhold = it.json("innhold", objectMapper),
            sensitivitet = it.string("sensitivitet").let(Sensitivitet::parse),
            eksternVarsling = it.optionalJson("eksternVarslingStatus", objectMapper),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTimeOrNull("inaktivert"),
            inaktivertAv = it.stringOrNull("inaktivertAv")
        )
    }
}
