package no.nav.tms.varsel.authority.migrate

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import no.nav.tms.token.support.azure.exchange.AzureService
import no.nav.tms.token.support.azure.exchange.service.AzureHeader
import no.nav.tms.varsel.authority.VarselType
import java.text.DateFormat
import java.time.ZonedDateTime

class SiphonConsumer(
    private val varselSiphonClientId: String,
    private val azureService: AzureService,
) {
    private val varselSiphonBaseURL = "http://tms-varsel-siphon"

    private val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                dateFormat = DateFormat.getDateTimeInstance()
            }
        }

        install(HttpTimeout)
    }

    suspend fun fetchVarsler(type: VarselType, from: ZonedDateTime, to: ZonedDateTime, max: Int): List<LegacyVarsel> {
        return httpClient.get("$varselSiphonBaseURL/varsler") {
            setAzureHeader()
            setQueryParams(type, from, to, max)
        }.body()
    }

    suspend fun fetchArkivVarsler(type: VarselType, from: ZonedDateTime, to: ZonedDateTime, max: Int): List<LegacyArkivertVarsel> {
        return httpClient.get("$varselSiphonBaseURL/arkiv/varsler") {
            setAzureHeader()
            setQueryParams(type, from, to, max)
        }.body()
    }

    private fun HttpRequestBuilder.setQueryParams(type: VarselType, from: ZonedDateTime, to: ZonedDateTime, max: Int) {
        url {
            parameters.append("type", type.lowercaseName)
            parameters.append("fraDato", from.toString())
            parameters.append("tilDato", to.toString())
            parameters.append("max", max.toString())
        }
    }

    private suspend fun HttpRequestBuilder.setAzureHeader() {
        val token = azureService.getAccessToken(varselSiphonClientId)

        header(AzureHeader.Authorization, "Bearer $token")
    }
}
