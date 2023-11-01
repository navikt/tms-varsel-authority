package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.action.Varseltype.*
import no.nav.tms.varsel.authority.config.Source
import no.nav.tms.varsel.authority.config.VarselMetricsReporter

fun Route.saksbehandlerVarselApi(readRepository: ReadVarselRepository) {

    suspend fun PipelineContext<Unit, ApplicationCall>.fetchVarslerAndRespond(
        ident: String,
        type: Varseltype? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        VarselMetricsReporter.registerVarselHentet(type,Source.SAKSBEHANDLER)
        call.respond(readRepository.getDetaljertVarselForUser(ident, type = type, aktiv = aktiv))
    }

    get("/varsel/detaljert/alle") {
        fetchVarslerAndRespond(ident = call.request.identHeader)
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
