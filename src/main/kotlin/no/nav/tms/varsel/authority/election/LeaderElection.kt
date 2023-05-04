package no.nav.tms.varsel.authority.election

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import no.nav.personbruker.dittnav.common.util.config.UrlEnvVar
import java.net.InetAddress
import java.net.URL
import java.time.Instant
import java.time.ZonedDateTime

class LeaderElection(
    private val electionPath: URL = UrlEnvVar.getEnvVarAsURL("ELECTOR_PATH"),
    private val podName: String = InetAddress.getLocalHost().hostName,
    private val queryIntervalSeconds: Long = 60L
) {
    private var isLeader: Boolean = false
    private var previousQuery: Instant? = null

    suspend fun isLeader(): Boolean {
        if (shouldQueryForLeader()) {
            queryForLeader()
        }

        return isLeader
    }

    private val httpCLient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson()
        }

        install(HttpTimeout)
    }

    private suspend fun queryForLeader() {
        val response: ElectorResponse = httpCLient.get(electionPath).body()

        isLeader = response.name == podName

        previousQuery = Instant.now()
    }

    private fun shouldQueryForLeader(): Boolean {
        return if(previousQuery == null) {
            true
        } else {
            (Instant.now().epochSecond - previousQuery!!.epochSecond) > queryIntervalSeconds
        }
    }
}

private data class ElectorResponse(
    val name: String,
    @JsonProperty("last_update") val lastUpdate: ZonedDateTime
)
