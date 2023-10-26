package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.varsel.authority.LocalDateTimeHelper
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.toJson
import no.nav.tms.varsel.authority.write.aktiver.*
import no.nav.tms.varsel.authority.write.aktiver.AktiverVarselSink
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

private fun inaktivertEvent(varselId: String, kilde: VarselInaktivertKilde) = InaktivertAvAggregatorEvent(
    eventId = varselId,
    varselType = Varseltype.Beskjed,
    namespace = "namespace",
    appnavn = "appnavn",
    kilde = kilde,
    tidspunkt = LocalDateTimeHelper.nowAtUtc(),
)

private data class InaktivertAvAggregatorEvent(
    val eventId: String,
    val varselType: Varseltype,
    val namespace: String,
    val appnavn: String,
    val kilde: VarselInaktivertKilde,
    val tidspunkt: LocalDateTime
) {
    @JsonProperty("@event_name") val eventName = "inaktivert"
    @JsonProperty("@source") val source = "aggregator"
}
