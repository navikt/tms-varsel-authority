package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Route.detaljertVarselApi(readRepository: ReadVarselRepository) {

    suspend fun RoutingContext.fetchVarslerAndRespond(
        ident: String,
        type: Varseltype? = null,
        aktiv: Boolean? = null,
        timeRange: Timerange? = null
    ) = withContext(Dispatchers.IO) {
        VarselMetricsReporter.registerVarselHentet(type, Source.SAKSBEHANDLER)
        call.respond(readRepository.getDetaljertVarselForUser(ident, type = type, aktiv = aktiv, timeRange=timeRange))
    }

    get("/varsel/detaljert/alle") {

        fetchVarslerAndRespond(ident = call.request.identHeader, timeRange = call.timeRange())
    }

    get("/varsel/detaljert/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, aktiv = true)
    }

    get("/varsel/detaljert/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, aktiv = false)
    }

    get("/beskjed/detaljert/alle") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Beskjed)
    }

    get("/beskjed/detaljert/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Beskjed, aktiv = true)
    }

    get("/beskjed/detaljert/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Beskjed, aktiv = false)
    }

    get("/oppgave/detaljert/alle") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Oppgave)
    }

    get("/oppgave/detaljert/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Oppgave, aktiv = true)
    }

    get("/oppgave/detaljert/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Oppgave, aktiv = false)
    }

    get("/innboks/detaljert/alle") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Innboks)
    }

    get("/innboks/detaljert/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Innboks, aktiv = true)
    }

    get("/innboks/detaljert/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Innboks, aktiv = false)
    }
}

private val ApplicationRequest.identHeader get() = headers["ident"] ?: throw BadRequestException("Mangler ident-header")

class Timerange(fomQueryParam: String, tomQueryParam: String) {
    val fom = LocalDate.parse(fomQueryParam, formatter).atStartOfDay(ZoneId.of("Europe/Oslo"))
    val tom = LocalDate.parse(tomQueryParam, formatter).atStartOfDay(ZoneId.of("Europe/Oslo"))

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

private fun ApplicationCall.timeRange(): Timerange? {
    val fom = this.request.queryParameters["fom"]
    val tom = this.request.queryParameters["tom"]

    return if (fom != null && tom != null) {
        Timerange(fom, tom)
    } else null
}