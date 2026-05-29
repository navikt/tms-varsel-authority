package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.write.RetryingKafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class VarselInaktivertProducer(
    private val kafkaProducer: RetryingKafkaProducer,
    private val topicName: String,
) {
    private val log = KotlinLogging.logger {}

    private val objectMapper = defaultObjectMapper()

    fun varselInaktivert(hendelse: VarselInaktivertHendelse) {

        kafkaProducer.send(ProducerRecord(topicName, hendelse.varselId, objectMapper.writeValueAsString(hendelse)))

        log.info { "inaktivert-event produsert til kafka" }
    }
}

data class VarselInaktivertHendelse(
    val varselId: String,
    val varseltype: Varseltype,
    val produsent: DatabaseProdusent,
    @JsonIgnore val kilde: VarselInaktivertKilde
) {
    @JsonProperty("kilde") val inaktivertKilde = kilde.lowercaseName
    @JsonProperty("@event_name") val eventName = "inaktivert"
    val tidspunkt = nowAtUtc()
}
