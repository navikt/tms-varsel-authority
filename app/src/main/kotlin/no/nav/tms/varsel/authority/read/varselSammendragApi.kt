package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.auth.principal
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.token.support.user.token.verification.LevelOfAssurance
import no.nav.tms.token.support.user.token.verification.UserPrincipal
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.config.Source.BRUKER
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.action.Sensitivitet

fun Route.varselSammendragApi(readRepository: ReadVarselRepository) {

    suspend fun RoutingContext.fetchVarslerAndRespond(
        user: UserPrincipal,
        type: Varseltype? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {

        val varsler = readRepository.getVarselSammendragForUser(user.ident, type = type, aktiv = aktiv)
            .toSammendrag(
                maskerSensitive = loaIsLowerThanHigh(user),
                spraakkode = call.request.preferertSpraak
            )

        VarselMetricsReporter.registerVarselHentet(type,BRUKER, user.levelOfAssurance)
        call.respond(varsler)
    }


    // {type} = [beskjed, oppgave, innboks]
    // {aktiv} = [alle, aktive, inaktive]
    get("/{type}/sammendrag/{aktiv}") {
        fetchVarslerAndRespond(
            user = call.user,
            type = call.typeFilterFromPath(),
            aktiv = call.aktivFilterFromPath()
        )
    }

    get("/varsel/sammendrag") {
        fetchVarslerAndRespond(
            user = call.user,
            type = call.request.typeFromQueryParam,
            aktiv = call.request.aktivFromQueryParam
        )
    }
}

private val ApplicationRequest.preferertSpraak get() = queryParameters["preferert_spraak"]?.lowercase()
private val ApplicationRequest.typeFromQueryParam get() = queryParameters["type"]?.let(Varseltype::parse)
private val ApplicationRequest.aktivFromQueryParam get() = queryParameters["aktiv"]?.lowercase()?.toBooleanStrict()

private fun RoutingCall.typeFilterFromPath(): Varseltype? {
    return try {
        when (val filter = request.pathVariables["type"]!!) {
            "varsel" -> null
            else -> Varseltype.parse(filter)
        }
    } catch (e: Exception) {
        throw NotFoundException("Ugyldig varseltype-filter i path")
    }
}

private fun RoutingCall.aktivFilterFromPath(): Boolean? {
    return when(request.pathVariables["aktiv"]?.lowercase()) {
        "alle" -> null
        "aktive" -> true
        "inaktive" -> false
        else -> throw NotFoundException("Ugyldig aktiv-filter i path")
    }
}


private val ApplicationCall.user get() = principal<UserPrincipal>() ?: throw IllegalStateException("Fant ikke UserPrincipall i ApplicationCall")

private fun loaIsLowerThanHigh(user: UserPrincipal) = user.levelOfAssurance != LevelOfAssurance.High

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
