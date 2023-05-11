package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.tms.varsel.authority.write.sink.VarselType.*

fun Route.systemVarselApi(readRepository: ReadVarselRepository) {
    get("/system/system/varsel/all") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader))
    }

    get("/system/system/varsel/aktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, aktiv = true))
    }

    get("/system/system/varsel/inaktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, aktiv = false))
    }

    get("/system/beskjed/all") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Beskjed))
    }

    get("/system/beskjed/aktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Beskjed, aktiv = true))
    }

    get("/system/beskjed/inaktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Beskjed, aktiv = false))
    }

    get("/system/oppgave/all") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Oppgave))
    }

    get("/system/oppgave/aktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Oppgave, aktiv = true))
    }

    get("/system/oppgave/inaktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Oppgave, aktiv = false))
    }

    get("/system/innboks/all") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Innboks))
    }

    get("/system/innboks/aktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Innboks, aktiv = true))
    }

    get("/system/innboks/inaktive") {
        call.respond(readRepository.getVarselForUserFull(call.request.identHeader, type = Innboks, aktiv = false))
    }
}

private val ApplicationRequest.identHeader get() = headers["ident"] ?: throw BadRequestException("Mangler ident-header")
