package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.token.support.tokenx.validation.user.TokenXUserFactory
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.VarselType.*

fun Route.brukerVarselApi(readRepository: ReadVarselRepository) {

    suspend fun PipelineContext<Unit, ApplicationCall>.fetchVarslerAndRespond(
        ident: String,
        type: VarselType? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        call.respond(readRepository.getVarselSammendragForUser(ident, type = type, aktiv = aktiv))
    }

    get("/varsel/all") {
        fetchVarslerAndRespond(ident = call.ident)
    }

    get("/varsel/aktive") {
        fetchVarslerAndRespond(ident = call.ident, aktiv = true)
    }

    get("/varsel/inaktive") {
        fetchVarslerAndRespond(ident = call.ident, aktiv = false)
    }

    get("/beskjed/all") {
        fetchVarslerAndRespond(ident = call.ident, type = Beskjed)
    }

    get("/beskjed/aktive") {
        fetchVarslerAndRespond(ident = call.ident, type = Beskjed, aktiv = true)
    }

    get("/beskjed/inaktive") {
        fetchVarslerAndRespond(ident = call.ident, type = Beskjed, aktiv = false)
    }

    get("/oppgave/all") {
        fetchVarslerAndRespond(ident = call.ident, type = Oppgave)
    }

    get("/oppgave/aktive") {
        fetchVarslerAndRespond(ident = call.ident, type = Oppgave, aktiv = true)
    }

    get("/oppgave/inaktive") {
        fetchVarslerAndRespond(ident = call.ident, type = Oppgave, aktiv = false)
    }

    get("/innboks/all") {
        fetchVarslerAndRespond(ident = call.ident, type = Innboks)
    }

    get("/innboks/aktive") {
        fetchVarslerAndRespond(ident = call.ident, type = Innboks, aktiv = true)
    }

    get("/innboks/inaktive") {
        fetchVarslerAndRespond(ident = call.ident, type = Innboks, aktiv = false)
    }
}

private val ApplicationCall.ident get() = TokenXUserFactory.createTokenXUser(this).ident
