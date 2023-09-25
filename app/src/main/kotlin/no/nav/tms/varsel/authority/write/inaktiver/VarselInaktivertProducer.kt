package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class VarselInaktivertProducer(
    private val kafkaProducer: Producer<String, String>,
    private val topicName: String,
) {
    private val log = KotlinLogging.logger {}

    private val objectMapper = defaultObjectMapper()

    fun varselInaktivert(hendelse: VarselInaktivertHendelse) {

        kafkaProducer.send(ProducerRecord(topicName, hendelse.varselId, objectMapper.writeValueAsString(hendelse)))
    }

    fun flushAndClose() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            log.info { "Produsent for kafka-eventer er flushet og lukket." }
        } catch (e: Exception) {
            log.warn { "Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert." }
        }
    }
}

data class VarselInaktivertHendelse(
    val varselId: String,
    val varselType: VarselType,
    val namespace: String,
    val appnavn: String,
    @JsonIgnore val kilde: VarselInaktivertKilde
) {
    @JsonProperty("kilde") val inaktivertKilde = kilde.lowercaseName
    @JsonProperty("@event_name") val eventName = "inaktivert"
    @JsonProperty("@source") val source = "varsel-authority"
    val tidspunkt = nowAtUtc()
}
