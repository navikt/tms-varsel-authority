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
import no.nav.tms.common.metrics.installTmsApiMetrics
import no.nav.tms.token.support.azure.validation.AzureAuthenticator
import no.nav.tms.token.support.azure.validation.azure
import no.nav.tms.token.support.tokenx.validation.tokenX
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.read.detaljertVarselApi
import no.nav.tms.varsel.authority.read.varselSammendragApi
import no.nav.tms.varsel.authority.write.inaktiver.*
import no.nav.tms.common.observability.ApiMdc
import no.nav.tms.varsel.action.VarselIdException
import java.text.DateFormat

fun Application.varselApi(
    readVarselRepository: ReadVarselRepository,
    varselInaktiverer: VarselInaktiverer,
    installAuthenticatorsFunction: Application.() -> Unit = installAuth(),
) {

    val log = KotlinLogging.logger {}

    installAuthenticatorsFunction()

    install(ApiMdc)

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
                is UnprivilegedAccessException, is VarselNotFoundException, is VarselIdException -> {
                    call.respondText(
                        status = HttpStatusCode.Forbidden,
                        text = "feilaktig varselId"
                    )
                    log.warn(cause) { cause.message }
                }

                is InvalidVarseltypeException -> {
                    call.respondText(
                        status = HttpStatusCode.Forbidden,
                        text = "Bruker kan ikke inaktivere ${cause.type} via api"
                    )
                    log.warn(cause) { cause.message }
                }

                is IllegalArgumentException -> {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = cause.message ?: "Feil i parametre"
                    )
                    log.warn(cause) { "Feil i parametre" }
                }

                else -> {
                    call.respond(HttpStatusCode.InternalServerError)
                    log.warn(cause) { "Apikall feiler" }
                }
            }

        }
    }


    routing {
        authenticate {
            inaktiverBeskjedApi(varselInaktiverer)
            varselSammendragApi(readVarselRepository)
        }
        authenticate(AzureAuthenticator.name) {
            inaktiverVarselApi(varselInaktiverer)
            detaljertVarselApi(readVarselRepository)
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
