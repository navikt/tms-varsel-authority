package no.nav.tms.varsel.authority.done

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.tms.varsel.authority.common.database.log
import no.nav.tms.token.support.authentication.installer.installAuthenticators
import no.nav.tms.token.support.tokenx.validation.user.TokenXUserFactory


fun Application.doneApi(
    varselUpdater: VarselBrukerService,
    installAuthenticatorsFunction: Application.() -> Unit = installAuth(),
) {

    installAuthenticatorsFunction()

    install(ContentNegotiation) {
        jackson {
            configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is IllegalArgumentException -> {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = cause.message ?: "Feil i parametre"
                    )

                    log.warn(cause.message, cause.stackTrace)
                }

                is UnprivilegedAccessException -> {
                    call.respondText(
                        status = HttpStatusCode.Forbidden,
                        text = "feilaktig varselId"
                    )
                }

                else -> call.respond(HttpStatusCode.InternalServerError)
            }

        }
    }


    routing {
        authenticate {
            route("beskjed/done") {
                post {
                    varselUpdater.inaktiverVarsel(call.varselId(), call.tokenXUser.ident)

                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}

private suspend fun ApplicationCall.varselId(): String =
    receive<EventIdBody>().eventId ?: throw EventIdMissingException()

private fun installAuth(): Application.() -> Unit = {
    installAuthenticators {
        installTokenXAuth {
            setAsDefault = true
        }
        installAzureAuth {
            setAsDefault = false
        }
    }
}

private val ApplicationCall.tokenXUser get() = TokenXUserFactory.createTokenXUser(this)

data class EventIdBody(val eventId: String? = null)

class EventIdMissingException : IllegalArgumentException("eventid parameter mangler")
