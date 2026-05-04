package no.nav.tms.varsel.authority.write.inaktiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.tms.token.support.user.token.verification.UserPrincipal
import no.nav.tms.varsel.action.VarselIdValidator
import java.lang.IllegalStateException

fun Route.inaktiverBeskjedApi(
    varselUpdater: VarselInaktiverer
) {
    post("/beskjed/inaktiver") {
        varselUpdater.inaktiverBeskjedForBruker(call.varselId(), call.user.ident)

        call.respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.varselId(): String {
    val requestBody = receive<VarselIdBody>()

    if (requestBody.varselId == null) {
        throw VarselIdMissingException()
    }

    VarselIdValidator.validate(requestBody.varselId)

    return requestBody.varselId
}

private val ApplicationCall.user get() = principal<UserPrincipal>() ?: throw IllegalStateException("Fant ikke UserPrincipal i ApplicationCall")


data class VarselIdBody(val varselId: String? = null)

class VarselIdMissingException : IllegalArgumentException("varselId parameter mangler")
