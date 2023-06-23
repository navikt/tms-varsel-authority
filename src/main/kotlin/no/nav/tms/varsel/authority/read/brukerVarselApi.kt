package no.nav.tms.varsel.authority.read

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.token.support.tokenx.validation.LevelOfAssurance
import no.nav.tms.token.support.tokenx.validation.user.TokenXUser
import no.nav.tms.token.support.tokenx.validation.user.TokenXUserFactory
import no.nav.tms.varsel.authority.Sensitivitet
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.VarselType.*

fun Route.brukerVarselApi(readRepository: ReadVarselRepository) {

    suspend fun PipelineContext<Unit, ApplicationCall>.fetchVarslerAndRespond(
        user: TokenXUser,
        type: VarselType? = null,
        aktiv: Boolean? = null
    ) = withContext(Dispatchers.IO) {

        val varsler = if (loaIsLowerThanHigh(user)) {
            readRepository.getVarselSammendragForUser(user.ident, type = type, aktiv = aktiv).maskInnhold()
        } else {
            readRepository.getVarselSammendragForUser(user.ident, type = type, aktiv = aktiv)
        }

        call.respond(varsler)
    }

    get("/varsel/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user)
    }

    get("/varsel/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, aktiv = true)
    }

    get("/varsel/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, aktiv = false)
    }

    get("/beskjed/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user, type = Beskjed)
    }

    get("/beskjed/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, type = Beskjed, aktiv = true)
    }

    get("/beskjed/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, type = Beskjed, aktiv = false)
    }

    get("/oppgave/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user, type = Oppgave)
    }

    get("/oppgave/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, type = Oppgave, aktiv = true)
    }

    get("/oppgave/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, type = Oppgave, aktiv = false)
    }

    get("/innboks/sammendrag/alle") {
        fetchVarslerAndRespond(user = call.user, type = Innboks)
    }

    get("/innboks/sammendrag/aktive") {
        fetchVarslerAndRespond(user = call.user, type = Innboks, aktiv = true)
    }

    get("/innboks/sammendrag/inaktive") {
        fetchVarslerAndRespond(user = call.user, type = Innboks, aktiv = false)
    }
}

private val ApplicationCall.user get() = TokenXUserFactory.createTokenXUser(this)

private fun loaIsLowerThanHigh(user: TokenXUser) = user.levelOfAssurance != LevelOfAssurance.HIGH

private fun List<Varselsammendrag>.maskInnhold() = map { varsel ->
    if (varsel.sensitivitet == Sensitivitet.High) {
        varsel.copy(innhold = null)
    } else {
        varsel.copy()
    }
}
