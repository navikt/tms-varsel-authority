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

fun Route.debugVarselApi(readRepository: ReadVarselRepository) {

    suspend fun PipelineContext<Unit, ApplicationCall>.fetchVarslerAndRespond(
        type: VarselType? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = type, aktiv = aktiv))
    }

    get("/debug/varsel/summary/all") {
        fetchVarslerAndRespond()
    }

    get("/debug/varsel/summary/aktive") {
        fetchVarslerAndRespond(aktiv = true)
    }

    get("/debug/varsel/summary/inaktive") {
        fetchVarslerAndRespond(aktiv = false)
    }

    get("/debug/varsel/details/all") {
        fetchVarslerAndRespond()
    }

    get("/debug/varsel/details/aktive") {
        fetchVarslerAndRespond(aktiv = true)
    }

    get("/debug/varsel/details/inaktive") {
        fetchVarslerAndRespond(aktiv = false)
    }
}

private val ApplicationRequest.identHeader get() = headers["ident"] ?: throw BadRequestException("Mangler ident-header")
