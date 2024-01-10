package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseProdusent
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

        log.info { "eksternStatusOppdatert-event produsert til kafka" }
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

data class EksternStatusOppdatering(
    val status: EksternStatus,
    val varselId: String,
    val ident: String,
    val varseltype: Varseltype,
    val produsent: DatabaseProdusent,
    val kanal: String?,
    val renotifikasjon: Boolean?,
    val feilmelding: String?
) {
    @JsonProperty("@event_name") val eventName = "eksternStatusOppdatert"
    val tidspunkt = nowAtUtc()
}
