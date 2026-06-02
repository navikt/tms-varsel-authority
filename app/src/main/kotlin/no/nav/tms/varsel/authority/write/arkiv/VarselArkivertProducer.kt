package no.nav.tms.varsel.authority.write.arkiv

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.write.outgoing.QueueableKafkaProducer
import no.nav.tms.varsel.authority.write.outgoing.KafkaProducerException
import java.time.ZonedDateTime

class VarselArkivertProducer(
    private val kafkaProducer: QueueableKafkaProducer,
    private val topicName: String
) {
    private val objectMapper = defaultObjectMapper()

    fun varselArkivert(arkivVarsel: ArkivVarsel) {

        val hendelseJson = VarselArkivertHendelse(
            varselId = arkivVarsel.varselId,
            varseltype = arkivVarsel.type,
            produsent = arkivVarsel.produsent,
            opprettet = arkivVarsel.opprettet
        ).let(objectMapper::writeValueAsString)

        try {
            kafkaProducer.send(topicName, arkivVarsel.varselId, hendelseJson)
        } catch (e: KafkaProducerException) {
            kafkaProducer.enqueue(topicName, arkivVarsel.varselId, hendelseJson)
        }
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
