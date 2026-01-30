package no.nav.tms.varsel.authority.read

import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.action.Varseltype.*
import no.nav.tms.varsel.authority.config.Source
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.Timerange

fun Route.detaljertVarselApi(readRepository: ReadVarselRepository) {

    suspend fun RoutingContext.fetchVarslerAndRespond(
        ident: String,
        type: Varseltype? = null,
        aktiv: Boolean? = null,
    ) = withContext(Dispatchers.IO) {
        VarselMetricsReporter.registerVarselHentet(type, Source.SAKSBEHANDLER)

        call.respond(readRepository.getDetaljertVarselForUser(ident, type = type, aktiv = aktiv, timeRange = null))
    }

    // {type} = [beskjed, oppgave, innboks]
    // {aktiv} = [alle, aktive, inaktive]
    post("/{type}/detaljert/{aktiv}") {
        fetchVarslerAndRespond(
            ident = call.identFromBody(),
            type = call.typeFilterFromPath(),
            aktiv = call.aktivFilterFromPath()
        )
    }

    // Deprecated TODO: Fjern get med ident-header
    get("/{type}/detaljert/{aktiv}") {
        fetchVarslerAndRespond(
            ident = call.request.identFromHeader,
            type = call.typeFilterFromPath(),
            aktiv = call.aktivFilterFromPath()
        )
    }
}

private data class IdentBody(
    val ident: String
)

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

private val ApplicationRequest.identFromHeader get() = headers["ident"] ?: throw BadRequestException("Mangler ident-header")
private suspend fun RoutingCall.identFromBody() = receive<IdentBody>().ident
