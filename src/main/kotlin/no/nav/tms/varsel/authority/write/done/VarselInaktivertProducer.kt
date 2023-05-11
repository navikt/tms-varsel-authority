package no.nav.tms.varsel.authority.write.done

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.sink.VarselType
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

class VarselInaktivertProducer(
    private val kafkaProducer: Producer<String, String>,
    private val topicName: String,
) {
    val log: Logger = LoggerFactory.getLogger(Producer::class.java)

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun varselInaktivert(hendelse: VarselInaktivertHendelse) {

        val producerRecord = ProducerRecord(topicName, hendelse.varselId, objectMapper.writeValueAsString(hendelse))

        kafkaProducer.send(producerRecord)
    }

    fun flushAndClose() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            log.info("Produsent for kafka-eventer er flushet og lukket.")
        } catch (e: Exception) {
            log.warn("Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert.")
        }
    }
}

data class VarselInaktivertHendelse(
    val varselId: String,
    val varselType: VarselType,
    val namespace: String,
    val appnavn: String,
    @JsonIgnore val kilde: VarselInaktivertKilde,
    val tidspunkt: ZonedDateTime = nowAtUtc()
) {
    @JsonProperty("kilde") val inaktivertKilde = kilde.lowercaseName
    @JsonProperty("@event_name") val eventName = "inaktivert"
}
