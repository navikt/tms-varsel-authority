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
        type: VarselType? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = type, aktiv = aktiv))
    }

    get("/varsel/all") {
        fetchVarslerAndRespond()
    }

    get("/varsel/aktive") {
        fetchVarslerAndRespond(aktiv = true)
    }

    get("/varsel/inaktive") {
        fetchVarslerAndRespond(aktiv = false)
    }

    get("/beskjed/all") {
        fetchVarslerAndRespond(type = Beskjed)
    }

    get("/beskjed/aktive") {
        fetchVarslerAndRespond(type = Beskjed, aktiv = true)
    }

    get("/beskjed/inaktive") {
        fetchVarslerAndRespond(type = Beskjed, aktiv = false)
    }

    get("/oppgave/all") {
        fetchVarslerAndRespond(type = Oppgave)
    }

    get("/oppgave/aktive") {
        fetchVarslerAndRespond(type = Oppgave, aktiv = true)
    }

    get("/oppgave/inaktive") {
        fetchVarslerAndRespond(type = Oppgave, aktiv = false)
    }

    get("/innboks/all") {
        fetchVarslerAndRespond(type = Innboks)
    }

    get("/innboks/aktive") {
        fetchVarslerAndRespond(type = Innboks, aktiv = true)
    }

    get("/innboks/inaktive") {
        fetchVarslerAndRespond(type = Innboks, aktiv = false)
    }
}

private val ApplicationCall.ident get() = TokenXUserFactory.createTokenXUser(this).ident
