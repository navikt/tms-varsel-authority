package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class EksternVarslingOppdatertProducer(private val kafkaProducer: Producer<String, String>,
                                       private val topicName: String
) {
    private val log = KotlinLogging.logger { }
    private val objectMapper = defaultObjectMapper()

    fun eksternStatusOppdatert(oppdatering: EksternStatusOppdatering) {

        val producerRecord = ProducerRecord(topicName, oppdatering.varselId, objectMapper.writeValueAsString(oppdatering))

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

data class EksternStatusOppdatering(
    val status: EksternStatus,
    val varselId: String,
    val ident: String,
    val varselType: VarselType,
    val namespace: String,
    val appnavn: String,
    val kanal: String?,
    val renotifikasjon: Boolean?
) {
    @JsonProperty("@event_name") val eventName = "eksternStatusOppdatert"
    @JsonProperty("@source") val source = "varsel-authority"
    val tidspunkt = nowAtUtc()
}
