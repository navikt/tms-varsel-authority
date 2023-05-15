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

fun Route.systemVarselApi(readRepository: ReadVarselRepository) {

    suspend fun PipelineContext<Unit, ApplicationCall>.fetchVarslerAndRespond(
        type: VarselType? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = type, aktiv = aktiv))
    }

    get("/system/varsel/all") {
        fetchVarslerAndRespond()
    }

    get("/system/varsel/aktive") {
        fetchVarslerAndRespond(aktiv = true)
    }

    get("/system/varsel/inaktive") {
        fetchVarslerAndRespond(aktiv = false)
    }

    get("/system/beskjed/all") {
        fetchVarslerAndRespond(type = Beskjed)
    }

    get("/system/beskjed/aktive") {
        fetchVarslerAndRespond(type = Beskjed, aktiv = true)
    }

    get("/system/beskjed/inaktive") {
        fetchVarslerAndRespond(type = Beskjed, aktiv = false)
    }

    get("/system/oppgave/all") {
        fetchVarslerAndRespond(type = Oppgave)
    }

    get("/system/oppgave/aktive") {
        fetchVarslerAndRespond(type = Oppgave, aktiv = true)
    }

    get("/system/oppgave/inaktive") {
        fetchVarslerAndRespond(type = Oppgave, aktiv = false)
    }

    get("/system/innboks/all") {
        fetchVarslerAndRespond(type = Innboks)
    }

    get("/system/innboks/aktive") {
        fetchVarslerAndRespond(type = Innboks, aktiv = true)
    }

    get("/system/innboks/inaktive") {
        fetchVarslerAndRespond(type = Innboks, aktiv = false)
    }
}

private val ApplicationRequest.identHeader get() = headers["ident"] ?: throw BadRequestException("Mangler ident-header")
