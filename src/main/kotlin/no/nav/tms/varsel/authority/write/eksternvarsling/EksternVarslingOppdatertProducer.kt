package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.sink.EksternStatus
import no.nav.tms.varsel.authority.write.sink.VarselType
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EksternVarslingOppdatertProducer(private val kafkaProducer: Producer<String, String>,
                                       private val topicName: String
) {
    val log: Logger = LoggerFactory.getLogger(Producer::class.java)
    private val objectMapper = jacksonObjectMapper()

    fun eksternStatusOppdatert(oppdatering: EksternStatusOppdatering) {

        val objectNode = objectMapper.createObjectNode()
        objectNode.put("@event_name", "eksternStatusOppdatert")
        objectNode.put("status", oppdatering.status.lowercaseName)
        objectNode.put("eventId", oppdatering.varselId)
        objectNode.put("ident", oppdatering.ident)
        objectNode.put("varselType", oppdatering.varselType.lowercaseName)
        objectNode.put("namespace", oppdatering.namespace)
        objectNode.put("appnavn", oppdatering.appnavn)
        objectNode.put("tidspunkt", nowAtUtc().toString())

        if (oppdatering.status == EksternStatus.Sendt) {
            objectNode.put("kanal", oppdatering.kanal)
            objectNode.put("renotifikasjon", oppdatering.renotifikasjon)
        }

        val producerRecord = ProducerRecord(topicName, oppdatering.varselId, objectNode.toString())

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
)
