package no.nav.tms.varsel.authority.write.inaktiver

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.tms.token.support.tokenx.validation.user.TokenXUserFactory
import no.nav.tms.varsel.action.VarselIdValidator

fun Route.inaktiverBeskjedApi(
    varselUpdater: VarselInaktiverer
) {
    post("/beskjed/inaktiver") {
        varselUpdater.inaktiverBeskjedForBruker(call.varselId(), call.tokenXUser.ident)

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

private val ApplicationCall.tokenXUser get() = TokenXUserFactory.createTokenXUser(this)


data class VarselIdBody(val varselId: String? = null)

class VarselIdMissingException : IllegalArgumentException("varselId parameter mangler")
