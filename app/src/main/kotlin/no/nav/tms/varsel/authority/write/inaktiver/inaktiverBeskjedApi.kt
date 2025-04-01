package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.tms.token.support.tokenx.validation.user.TokenXUserFactory
import no.nav.tms.varsel.action.VarselIdValidator
import no.nav.tms.varsel.authority.config.defaultObjectMapper

fun Route.inaktiverBeskjedApi(
    varselUpdater: VarselInaktiverer
) {
    post("/beskjed/inaktiver") {
        varselUpdater.inaktiverBeskjedForBruker(call.varselId(), call.tokenXUser.ident)

        call.respond(HttpStatusCode.OK)
    }
}

private val securelog = KotlinLogging.logger("secureLog")

private val localMapper = defaultObjectMapper()

private suspend fun ApplicationCall.varselId(): String {

    val body = receiveText()
    securelog.info { "Request body: $body" }

    val requestBody = localMapper.readValue<VarselIdBody>(body)

    if (requestBody.varselId == null) {
        throw VarselIdMissingException()
    }

    VarselIdValidator.validate(requestBody.varselId)

    return requestBody.varselId
}

private val ApplicationCall.tokenXUser get() = TokenXUserFactory.createTokenXUser(this)


data class VarselIdBody(val varselId: String? = null)

class VarselIdMissingException : IllegalArgumentException("varselId parameter mangler")
