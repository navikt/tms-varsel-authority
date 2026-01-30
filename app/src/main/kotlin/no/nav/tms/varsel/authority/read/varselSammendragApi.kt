package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
        throw BadRequestException("Ugyldig varseltype-filter i path")
    }
}

private fun RoutingCall.aktivFilterFromPath(): Boolean? {
    return when(request.pathVariables["aktiv"]?.lowercase()) {
        "alle" -> null
        "aktive" -> true
        "inaktive" -> false
        else -> throw BadRequestException("Ugyldig aktiv-filter i path")
    }
}


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
