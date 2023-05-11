package no.nav.tms.varsel.authority.read

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.database.Database
import no.nav.tms.varsel.authority.common.database.json
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.done.VarselInaktivertKilde.Frist
import no.nav.tms.varsel.authority.write.sink.VarselType

class ReadVarselRepository(private val database: Database) {
    private val objectMapper = defaultObjectMapper()

    fun getVarselForUserAbbreviated(ident: String, type: VarselType? = null, aktiv: Boolean? = null): List<AbreviatedVarsel> {
        return database.list {
            queryOf("""
                select
                  varselId,
                  type,
                  aktiv,
                  innhold,
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
                .map(toAbbreviatedVarsel())
                .asList
        }
    }

    fun getVarselForUserFull(ident: String, type: VarselType? = null, aktiv: Boolean? = null): List<FullVarsel> {
        return database.list {
            queryOf("""
                select
                  varselId,
                  type,
                  aktiv,
                  produsent,
                  innhold,
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
                .map(toFullVarsel())
                .asList
        }
    }

    private fun toAbbreviatedVarsel(): (Row) -> AbreviatedVarsel = {
        AbreviatedVarsel(
            type = it.string("type"),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            tekst = it.string("tekst"),
            link = it.string("link"),
            sikkerhetsnivaa = it.int("sikkerhetsnivaa"),
            eksternVarslingSendt = it.boolean("eksternVarslingSendt"),
            eksternVarslingKanaler = it.json("eksternVarslingKanaler", objectMapper),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTimeOrNull("inaktivert"),
            fristUtløpt = it.stringOrNull("inaktivertAv") == Frist.lowercaseName,
        )
    }

    private fun toFullVarsel(): (Row) -> FullVarsel = {
        FullVarsel(
            type = it.string("type"),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            produsent = it.json("produsent", objectMapper),
            tekst = it.string("tekst"),
            link = it.string("link"),
            sikkerhetsnivaa = it.int("sikkerhetsnivaa"),
            eksternVarsling = it.json("eksternVarsling", objectMapper),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTime("inaktivert"),
            inaktivertAv = it.string("inaktivertAv")
        )
    }
}
