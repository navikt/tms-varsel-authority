package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.VarselType.*

fun Route.saksbehandlerVarselApi(readRepository: ReadVarselRepository) {

    suspend fun PipelineContext<Unit, ApplicationCall>.fetchVarslerAndRespond(
        ident: String,
        type: VarselType? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        call.respond(readRepository.getDetaljertVarselForUser(ident, type = type, aktiv = aktiv))
    }

    get("/system/varsel/all") {
        fetchVarslerAndRespond(ident = call.request.identHeader)
    }

    get("/system/varsel/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, aktiv = true)
    }

    get("/system/varsel/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, aktiv = false)
    }

    get("/system/beskjed/all") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Beskjed)
    }

    get("/system/beskjed/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Beskjed, aktiv = true)
    }

    get("/system/beskjed/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Beskjed, aktiv = false)
    }

    get("/system/oppgave/all") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Oppgave)
    }

    get("/system/oppgave/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Oppgave, aktiv = true)
    }

    get("/system/oppgave/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Oppgave, aktiv = false)
    }

    get("/system/innboks/all") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Innboks)
    }

    get("/system/innboks/aktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Innboks, aktiv = true)
    }

    get("/system/innboks/inaktive") {
        fetchVarslerAndRespond(ident = call.request.identHeader, type = Innboks, aktiv = false)
    }
}

private val ApplicationRequest.identHeader get() = headers["ident"] ?: throw BadRequestException("Mangler ident-header")
