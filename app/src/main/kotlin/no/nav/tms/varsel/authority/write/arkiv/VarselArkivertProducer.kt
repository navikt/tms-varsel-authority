package no.nav.tms.varsel.authority.write.arkiv

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.action.Varseltype
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.ZonedDateTime

class VarselArkivertProducer(
    private val kafkaProducer: Producer<String, String>,
    private val topicName: String
) {
    private val log = KotlinLogging.logger { }

    private val objectMapper = defaultObjectMapper()

    fun varselArkivert(arkivVarsel: ArkivVarsel) {

        val hendelse = VarselArkivertHendelse(
            varselId = arkivVarsel.varselId,
            varselType = arkivVarsel.type,
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
            log.info { "Produsent for kafka-eventer er flushet og lukket." }
        } catch (e: Exception) {
            log.warn { "Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert." }
        }
    }
}

private data class VarselArkivertHendelse(
    val varselId: String,
    val varselType: Varseltype,
    val namespace: String,
    val appnavn: String,
    val opprettet: ZonedDateTime,
    val tidspunkt: ZonedDateTime = ZonedDateTimeHelper.nowAtUtc()
) {
    @JsonProperty("@event_name") val eventName = "arkivert"
}
