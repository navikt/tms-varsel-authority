package no.nav.tms.varsel.authority

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.auth.*
import nav.no.tms.common.metrics.installTmsApiMetrics
import no.nav.tms.token.support.azure.validation.AzureAuthenticator
import no.nav.tms.token.support.azure.validation.azure
import no.nav.tms.token.support.tokenx.validation.tokenX
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.read.saksbehandlerVarselApi
import no.nav.tms.varsel.authority.read.brukerVarselApi
import no.nav.tms.varsel.authority.write.inaktiver.*
import java.text.DateFormat

fun Application.varselApi(
    readVarselRepository: ReadVarselRepository,
    beskjedInaktiverer: BeskjedInaktiverer,
    installAuthenticatorsFunction: Application.() -> Unit = installAuth(),
) {

    val log = KotlinLogging.logger {}

    installAuthenticatorsFunction()

    installTmsApiMetrics {
        setupMetricsRoute = false
    }

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

                is UnprivilegedAccessException, is VarselNotFoundException -> {
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

                is IllegalArgumentException -> {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = cause.message ?: "Feil i parametre"
                    )

                    log.warn { cause.message }
                }

                else -> {
                    call.respond(HttpStatusCode.InternalServerError)
                    log.warn(cause) { "Ukjent feil" }
                }
            }

        }
    }


    routing {
        authenticate {
            inaktiverBeskjedApi(beskjedInaktiverer)
            brukerVarselApi(readVarselRepository)
        }
        authenticate(AzureAuthenticator.name) {
            saksbehandlerVarselApi(readVarselRepository)
        }
    }
}

private fun installAuth(): Application.() -> Unit = {
    authentication {
        tokenX {
            setAsDefault = true
        }
        azure {
            setAsDefault = false
        }
    }
}
