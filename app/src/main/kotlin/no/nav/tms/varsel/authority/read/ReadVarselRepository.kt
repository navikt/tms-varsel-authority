package no.nav.tms.varsel.authority.read

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.common.postgres.JsonbHelper.json
import no.nav.tms.common.postgres.JsonbHelper.jsonOrNull
import no.nav.tms.common.postgres.PostgresDatabase
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.authority.common.*
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.read.DetaljertAdminVarsel.Companion.resolveInaktivert
import no.nav.tms.varsel.authority.write.inaktiver.Timerange

private val log = KotlinLogging.logger { }

class ReadVarselRepository(private val database: PostgresDatabase) {

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
               jsonb_exists(varsel , 'forstBehandlet') as fromLegacyJson,
               coalesce((varsel ->> 'opprettet'), (varsel ->> 'forstBehandlet'))::timestamp with time zone as opprettet,
               varsel ->> 'type'                                                                           as type,
               (varsel ->> 'aktiv')::boolean                                                                                      as aktiv,
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
        
        
               varsel ->> 'inaktivertAv'                                                                   as inaktivertAv,
               
               CASE
                   WHEN varsel ->> 'inaktivert' IS NULL OR varsel ->> 'inaktivert' = '' OR varsel ->> 'inaktivert' = 'null'
                        THEN NULL
                   ELSE (varsel ->> 'inaktivert')::timestamp with time zone
               END                                                                                         as inaktivert,
               varsel ->> 'fristUtlopt'                                                                    as fristUtlopt,
               true                                                                                        as arkivert
        from varsel_arkiv
                where ident = :ident
                    and (
                        coalesce((varsel ->> 'opprettet'), (varsel ->> 'forstBehandlet')) between :fom and :tom
                        or (varsel ->> 'inaktivert' is not null and (varsel ->> 'inaktivert')::timestamp between :fom and :tom)
                    )
                union
                select varselid,
                   false                                                                as fromLegacyJson,
                   opprettet::timestamp with time zone,
                   type,
                   aktiv,
                   innhold ->> 'tekst'                                                  as tekst,
                   innhold ->> 'link'                                                   as link,
                   NULL                                                                 as sikkerhetsnivaa,
                   sensitivitet                                                         as sensitivitet,
                   concat(produsent ->> 'appnavn', '(', produsent ->> 'namespace', ')') as produsent,
                   eksternvarslingstatus                                                as eksternVarsling,
                   inaktivertav                                                         as inaktivertAv,
                   CASE
                       WHEN inaktivert IS NULL THEN NULL
                       ELSE inaktivert::timestamp with time zone 
                   END                                                                  as inaktivert,
                   null                                                                 as fristUtlopt,
                   false                                                                as arkivert
               from varsel
                where ident = :ident
                  and (opprettet between :fom and :tom or inaktivert between :fom and :tom)
                order by opprettet desc;                
            """,
                mapOf(
                    "ident" to ident, "fom" to timeRange.fom, "tom" to timeRange.tom
                )
            ).map(toDetaljertAdminVarsel())
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
            type = it.string("type").let(Varseltype::parse),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            innhold = it.json("innhold"),
            sensitivitet = it.string("sensitivitet").let(Sensitivitet::parse),
            eksternVarslingSendt = it.boolean("eksternVarslingSendt"),
            eksternVarslingKanaler = it.jsonOrNull("eksternVarslingKanaler") ?: emptyList(),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTimeOrNull("inaktivert")
        )
    }

    private fun toDetaljertVarsel(): (Row) -> DetaljertVarsel = {
        DetaljertVarsel(
            type = it.string("type").let(Varseltype::parse),
            varselId = it.string("varselId"),
            aktiv = it.boolean("aktiv"),
            produsent = it.json("produsent"),
            innhold = it.json("innhold"),
            sensitivitet = it.string("sensitivitet").let(Sensitivitet::parse),
            eksternVarsling = it.jsonOrNull("eksternVarslingStatus"),
            opprettet = it.zonedDateTime("opprettet"),
            aktivFremTil = it.zonedDateTimeOrNull("aktivFremTil"),
            inaktivert = it.zonedDateTimeOrNull("inaktivert"),
            inaktivertAv = it.stringOrNull("inaktivertAv")
        )
    }

    private fun toDetaljertAdminVarsel(): (Row) -> Pair<DetaljertAdminVarsel?, String?> = {
        var errorStringId: String? = null
        var adminVarsel: DetaljertAdminVarsel? = null

        try {

            val readEksternVarsling: EksternVarslingArchiveCompatible? =
                it.jsonOrNull("eksternVarsling")

            val varselType = it.string("type").let(Varseltype::parse)

            adminVarsel = DetaljertAdminVarsel(
                type = varselType,
                varselId = it.string("varselId"),
                aktiv = it.boolean("aktiv"),
                produsertAv = it.string("produsent"),
                innhold = Innhold(tekst = it.string("tekst"), link = it.stringOrNull("link")),
                tilgangstyring = it.resolveTilgangstyring(),
                eksternVarsling = readEksternVarsling?.toEksternVarslingInfo(),
                opprettet = it.zonedDateTime("opprettet"),
                inaktivert = it.resolveInaktivert(varselType),
                arkivert = it.boolean("arkivert")
            )
        } catch (ex: Exception) {
            log.warn { "Feil ved lesing av arkivert varsel med id: ${it.stringOrNull("varselId") ?: "Not serializable"}" }
            errorStringId = it.stringOrNull("varselId") ?: "Not serializable"
        }

        Pair(adminVarsel, errorStringId)
    }

}

fun Row.booleanOrNull(columnLabel: String): Boolean? =
    this.anyOrNull(columnLabel) as? Boolean
