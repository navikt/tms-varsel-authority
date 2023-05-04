package no.nav.tms.varsel.authority.varsel

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.sink.DatabaseVarsel
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VarselAktivertProducer(
    private val kafkaProducer: Producer<String, String>,
    private val topicName: String
) {

    private val log: Logger = LoggerFactory.getLogger(Producer::class.java)

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()

    fun varselAktivert(dbVarsel: DatabaseVarsel) {
        val varselJson = dbVarsel.varsel.asJson()

        val eksternVarslingBestilling = dbVarsel.eksternVarslingBestilling?.asJson()

        varselJson.put("@event_name", "aktivert")
        varselJson.put("eksternVarslingBestilling", eksternVarslingBestilling)
        varselJson.put("tidspunkt", nowAtUtc().toString())

        val producerRecord = ProducerRecord(topicName, dbVarsel.varselId, varselJson.toString())
        kafkaProducer.send(producerRecord)
    }

    private fun Any.asJson(): ObjectNode {
        return objectMapper.valueToTree(this)
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
