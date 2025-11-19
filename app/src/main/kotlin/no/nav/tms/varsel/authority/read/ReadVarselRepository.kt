package no.nav.tms.varsel.authority.read

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.*
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.Innhold

private val log = KotlinLogging.logger { }

class ReadVarselRepository(private val database: Database) {
    private val objectMapper = defaultObjectMapper()

    fun getVarselSammendragForUser(
        ident: String,
        type: Varseltype? = null,
        aktiv: Boolean? = null
    ): List<DatabaseVarselsammendrag> {
        return database.list {
            queryOf(
                """
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
                    ${if (type != null) " and type = :type " else ""}
                    ${if (aktiv != null) " and aktiv = :aktiv " else ""}
            """,
                mapOf("ident" to ident, "type" to type?.name?.lowercase(), "aktiv" to aktiv)
            )
                .map(toVarselsammendrag())
                .asList
        }
    }

    fun getDetaljertVarselForUser(
        ident: String,
        type: Varseltype? = null,
        aktiv: Boolean? = null,
        timeRange: Timerange?
    ): List<DetaljertVarsel> {
        return database.list {
            queryOf(
                """
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
                    ${if (type != null) " and type = :type " else ""}
                    ${if (aktiv != null) " and aktiv = :aktiv " else ""}
                    ${if (timeRange != null) "and (opprettet between :fom and :tom or inaktivert between :fom and :tom)" else ""}
            """,
                mapOf(
                    "ident" to ident,
                    "type" to type?.name?.lowercase(),
                    "aktiv" to aktiv,
                    "fom" to timeRange?.fom,
                    "tom" to timeRange?.tom
                )
            )
                .map(toDetaljertVarsel())
                .asList
        }
    }

    fun getAlleVarselForUserIncludeArchived(
        ident: String,
        timeRange: Timerange
    ): ArchivedAndCurrentVarsler {

        val success = mutableListOf<DetaljertAdminVarsel>()
        val failed = mutableListOf<String>()

        val result = database.list {
            queryOf(
                """
                select varselid,
                       coalesce((varsel ->> 'opprettet'), (varsel ->> 'forstBehandlet'))::timestamp with time zone as opprettet,
                       varsel ->> 'type'                                                                           as type,
                       false                                                                                       as aktiv,
                       COALESCE(varsel -> 'innhold' ->> 'tekst', varsel ->> 'tekst')                               as tekst,
                       COALESCE(varsel -> 'innhold' ->> 'link', varsel ->> 'link')                                 as link,
                       varsel ->> 'sikkerhetsnivaa'                                                                as sikkerhetsnivaa,
                       varsel ->> 'sensitivitet'                                                                   as sensitivitet,
                       case
                           when varsel -> 'produsent' is not null
                               then concat(varsel -> 'produsent' ->> 'appnavn', '(', varsel -> 'produsent' ->> 'namespace', ')')
                           else varsel ->> 'produsentApp'
                           end                                                                                     as produsent,
                       case
                           when varsel -> 'eksternVarslingStatus' IS NOT NULL
                               then (varsel -> 'eksternVarslingStatus')
                           when (varsel -> 'eksternVarslingSendt' is not null) OR (varsel -> 'eksternVarslingKanaler' is not null)
                               then json_build_object('sendt', varsel ->> 'eksternVarslingSendt', 'kanaler',
                                                      varsel -> 'eksternVarslingKanaler')::jsonb
                           end                                                                                     as eksternVarsling,
                       case
                           when varsel ->> 'inaktivert' is not null and varsel ->> 'inaktivertAv' is not null
                               then concat(varsel ->> 'inaktivert', ' av ', varsel ->> 'inaktivertAv')
                           when varsel ->> 'inaktivert' is not null
                               then concat(varsel ->> 'inaktivert', ' av ukjent inaktiveringskilde')
                           end                                                                                     as inaktivert,
                       true                                                                                        as arkivert
                from varsel_arkiv
                where ident = :ident
                    and (
                        coalesce((varsel ->> 'opprettet'), (varsel ->> 'forstBehandlet')) between :fom and :tom
                        or (varsel ->> 'inaktivert' is not null and (varsel ->> 'inaktivert')::timestamp between :fom and :tom)
                    )
                union
                select varselid,
                       opprettet::timestamp with time zone,
                       type,
                       aktiv,
                       innhold ->> 'tekst'                                                  as tekst,
                       innhold ->> 'link'                                                   as link,
                       NULL                                                                 as sikkerhetsnivaa,
                       sensitivitet                                                         as sensitivitet,
                       concat(produsent ->> 'appnavn', '(', produsent ->> 'namespace', ')') as produsent,
                       eksternvarslingstatus                                                as eksternVarsling,
                       case
                           when inaktivert is not null and inaktivertav is not null
                               then
                               concat(inaktivert, ' av ', inaktivertav)
                           end                                                              as inaktivert,
                       false                                                                as arkivert
                from varsel
                where ident = :ident
                  and (opprettet between :fom and :tom or inaktivert between :fom and :tom)
                order by opprettet desc;                
            """,
                mapOf(
                    "ident" to ident, "fom" to timeRange.fom, "tom" to timeRange.tom
                )
            ).map(toDetaljertAdminVarsel()).asList
        }
        for ((adminVarsel, errorStringId) in result) {
            if (adminVarsel != null) {
                success.add(adminVarsel)
            } else if (errorStringId != null) {
                failed.add(errorStringId)
            }
        }
        return ArchivedAndCurrentVarsler(
            varsler = success,
            feilendeVarsler = failed
        )
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

    private fun toDetaljertAdminVarsel(): (Row) -> Pair<DetaljertAdminVarsel?, String?> = {

        val readEksternVarsling: EksternVarslingArchiveCompatible? = it.optionalJson("eksternVarsling", objectMapper)
        var adminVarsel: DetaljertAdminVarsel? = null
        var errorStringId: String? = null
        try {
            adminVarsel = DetaljertAdminVarsel(
                type = it.string("type").let(::parseVarseltype),
                varselId = it.string("varselId"),
                aktiv = it.boolean("aktiv"),
                produsertAv = it.string("produsent"),
                innhold = Innhold(tekst = it.string("tekst"), link = it.stringOrNull("link")),
                tilgangstyring = it.resolveTilgangstyring(),
                eksternVarsling = readEksternVarsling?.toEksternVarslingInfo(),
                opprettet = it.zonedDateTime("opprettet"),
                inaktivert = it.stringOrNull("inaktivert") ?: "Ukjent",
                arkivert = it.boolean("arkivert")
            )
        } catch (ex: Exception) {
            log.warn { "Feil ved lesing av arkivert varsel med id: ${ex.message}" }
            errorStringId = it.string("varselId")
        }

        Pair(adminVarsel, errorStringId)
    }

}
