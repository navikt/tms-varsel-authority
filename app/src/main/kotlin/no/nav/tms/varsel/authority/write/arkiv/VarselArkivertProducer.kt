package no.nav.tms.varsel.authority.write.arkiv

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.write.RetryingKafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.ZonedDateTime

class VarselArkivertProducer(
    private val kafkaProducer: RetryingKafkaProducer,
    private val topicName: String
) {
    private val objectMapper = defaultObjectMapper()

    fun varselArkivert(arkivVarsel: ArkivVarsel) {

        val hendelse = VarselArkivertHendelse(
            varselId = arkivVarsel.varselId,
            varseltype = arkivVarsel.type,
            produsent = arkivVarsel.produsent,
            opprettet = arkivVarsel.opprettet
        )

        kafkaProducer.send(ProducerRecord(topicName, hendelse.varselId, objectMapper.writeValueAsString(hendelse)))
    }
}

private data class VarselArkivertHendelse(
    val varselId: String,
    val varseltype: Varseltype,
    val produsent: DatabaseProdusent,
    val opprettet: ZonedDateTime,
    val tidspunkt: ZonedDateTime = ZonedDateTimeHelper.nowAtUtc()
) {
    @JsonProperty("@event_name") val eventName = "arkivert"
}
