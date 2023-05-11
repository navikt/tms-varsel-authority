package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.tms.token.support.tokenx.validation.user.TokenXUserFactory
import no.nav.tms.varsel.authority.write.sink.VarselType.*

fun Route.userVarselApi(readRepository: ReadVarselRepository) {
    get("/varsel/all") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident))
    }

    get("/varsel/aktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, aktiv = true))
    }

    get("/varsel/inaktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, aktiv = false))
    }

    get("/beskjed/all") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Beskjed))
    }

    get("/beskjed/aktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Beskjed, aktiv = true))
    }

    get("/beskjed/inaktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Beskjed, aktiv = false))
    }

    get("/oppgave/all") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Oppgave))
    }

    get("/oppgave/aktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Oppgave, aktiv = true))
    }

    get("/oppgave/inaktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Oppgave, aktiv = false))
    }

    get("/innboks/all") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Innboks))
    }

    get("/innboks/aktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Innboks, aktiv = true))
    }

    get("/innboks/inaktive") {
        call.respond(readRepository.getVarselForUserAbbreviated(call.ident, type = Innboks, aktiv = false))
    }
}

private val ApplicationCall.ident get() = TokenXUserFactory.createTokenXUser(this).ident
