package no.nav.tms.varsel.authority

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.tms.token.support.authentication.installer.installAuthenticators
import no.nav.tms.token.support.azure.validation.AzureAuthenticator
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.read.systemVarselApi
import no.nav.tms.varsel.authority.read.brukerVarselApi
import no.nav.tms.varsel.authority.read.debugVarselApi
import no.nav.tms.varsel.authority.write.inaktiver.BeskjedInaktiverer
import no.nav.tms.varsel.authority.write.inaktiver.InvalidVarselTypeException
import no.nav.tms.varsel.authority.write.inaktiver.UnprivilegedAccessException
import no.nav.tms.varsel.authority.write.inaktiver.inaktiverBeskjedApi
import java.text.DateFormat


fun Application.varselApi(
    readVarselRepository: ReadVarselRepository,
    varselUpdater: BeskjedInaktiverer,
    installAuthenticatorsFunction: Application.() -> Unit = installAuth(),
) {

    val log = KotlinLogging.logger {}

    installAuthenticatorsFunction()

    install(ContentNegotiation) {
        jackson {
            configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            registerModule(JavaTimeModule())
            dateFormat = DateFormat.getDateTimeInstance()
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

                is InvalidVarselTypeException -> {
                    call.respondText(
                        status = HttpStatusCode.Forbidden,
                        text = "kan kun inaktivere beskjed fra api"
                    )
                }

                else -> call.respond(HttpStatusCode.InternalServerError)
            }

        }
    }


    routing {
        debugVarselApi(readVarselRepository)
        authenticate {
            inaktiverBeskjedApi(varselUpdater)
            brukerVarselApi(readVarselRepository)
        }
        authenticate(AzureAuthenticator.name) {
            systemVarselApi(readVarselRepository)
        }
    }
}

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
