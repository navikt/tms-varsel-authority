package no.nav.tms.varsel.authority.write.archive

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.VarselType
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

class VarselArkivertProducer(
    private val kafkaProducer: Producer<String, String>,
    private val topicName: String
) {
    val log: Logger = LoggerFactory.getLogger(Producer::class.java)

    private val objectMapper = defaultObjectMapper()

    fun varselArkivert(arkivVarsel: ArchiveVarsel) {

        val hendelse = VarselArkivertHendelse(
            varselId = arkivVarsel.varselId,
            varselType = arkivVarsel.varsel.type,
            namespace = arkivVarsel.produsent.namespace,
            appnavn = arkivVarsel.produsent.appnavn,
            opprettet = arkivVarsel.opprettet
        )

        kafkaProducer.send(ProducerRecord(topicName, hendelse.varselId, objectMapper.writeValueAsString(hendelse)))
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

private data class VarselArkivertHendelse(
    val varselId: String,
    val varselType: VarselType,
    val namespace: String,
    val appnavn: String,
    val opprettet: ZonedDateTime,
    val tidspunkt: ZonedDateTime = ZonedDateTimeHelper.nowAtUtc()
) {
    @JsonProperty("@event_name") val eventName = "arkivert"
}
