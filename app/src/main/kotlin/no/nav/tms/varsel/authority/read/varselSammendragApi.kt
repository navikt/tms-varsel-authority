package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.token.support.tokenx.validation.LevelOfAssurance
import no.nav.tms.token.support.tokenx.validation.user.TokenXUser
import no.nav.tms.token.support.tokenx.validation.user.TokenXUserFactory
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.action.Varseltype.*
import no.nav.tms.varsel.authority.config.Source.BRUKER
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.authority.common.parseVarseltype

fun Route.varselSammendragApi(readRepository: ReadVarselRepository) {

    suspend fun RoutingContext.fetchVarslerAndRespond(
        user: TokenXUser,
        type: Varseltype? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {

        val varsler = readRepository.getVarselSammendragForUser(user.ident, type = type, aktiv = aktiv)
            .toSammendrag(
                maskerSensitive = loaIsLowerThanHigh(user),
                spraakkode = call.request.preferertSpraak
            )

        VarselMetricsReporter.registerVarselHentet(type,BRUKER,user.levelOfAssurance)
        call.respond(varsler)
    }

    get("/varsel/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user)
    }

    get("/varsel/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, aktiv = true)
    }

    get("/varsel/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, aktiv = false)
    }

    get("/beskjed/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user, type = Beskjed)
    }

    get("/beskjed/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, type = Beskjed, aktiv = true)
    }

    get("/beskjed/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, type = Beskjed, aktiv = false)
    }

    get("/oppgave/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user, type = Oppgave)
    }

    get("/oppgave/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, type = Oppgave, aktiv = true)
    }

    get("/oppgave/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, type = Oppgave, aktiv = false)
    }

    get("/innboks/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user, type = Innboks)
    }

    get("/innboks/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, type = Innboks, aktiv = true)
    }

    get("/innboks/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, type = Innboks, aktiv = false)
    }

    get("/varsel/sammendrag") {
        fetchVarslerAndRespond(
            user = call.user,
            type = call.request.type,
            aktiv = call.request.aktiv
        )
    }
}

private val ApplicationRequest.type get() = queryParameters["type"]?.let { parseVarseltype(it) }
private val ApplicationRequest.aktiv get() = queryParameters["aktiv"]?.lowercase()?.toBooleanStrict()
private val ApplicationRequest.preferertSpraak get() = queryParameters["preferert_spraak"]?.lowercase()


private val ApplicationCall.user get() = TokenXUserFactory.createTokenXUser(this)

private fun loaIsLowerThanHigh(user: TokenXUser) = user.levelOfAssurance != LevelOfAssurance.HIGH

private fun List<DatabaseVarselsammendrag>.toSammendrag(
    maskerSensitive: Boolean,
    spraakkode: String? = null
) = map {

    val innholdsammendrag = if (maskerSensitive && it.sensitivitet == Sensitivitet.High) {
        null
    } else {
        val tekst = it.tekstOrDefault(spraakkode)

        Innholdsammendrag(
            spraakkode = tekst.spraakkode,
            tekst = tekst.tekst,
            link = it.innhold.link
        )
    }

    Varselsammendrag(
        type = it.type,
        varselId = it.varselId,
        aktiv = it.aktiv,
        innhold = innholdsammendrag,
        eksternVarslingSendt = it.eksternVarslingSendt,
        eksternVarslingKanaler = it.eksternVarslingKanaler,
        opprettet = it.opprettet,
        aktivFremTil = it.aktivFremTil,
        inaktivert = it.inaktivert,
    )
}
