package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.ZonedDateTime

class VarselAktivertProducer(
    private val kafkaProducer: Producer<String, String>,
    private val topicName: String
) {

    private val log = KotlinLogging.logger { }

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()

    fun varselAktivert(dbVarsel: DatabaseVarsel) {

        val varselAktivertEvent = VarselAktivert.fromDatabaseVarsel(dbVarsel)

        val producerRecord = ProducerRecord(topicName, dbVarsel.varselId, varselAktivertEvent.asJson().toString())
        kafkaProducer.send(producerRecord)
    }

    private fun Any.asJson(): ObjectNode {
        return objectMapper.valueToTree(this)
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

private data class VarselAktivert(
    val type: VarselType,
    val varselId: String,
    val ident: String,
    val sensitivitet: Sensitivitet,
    val innhold: Innhold,
    val produsent: Produsent,
    val eksternVarslingBestilling: EksternVarslingBestilling?,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?
) {
    @JsonProperty("@event_name") val eventName = "aktivert"
    @JsonProperty("@source") val source = "varsel-authority"
    val tidspunkt = nowAtUtc()

    companion object {
        fun fromDatabaseVarsel(dbVarsel: DatabaseVarsel) = VarselAktivert(
             type = dbVarsel.type,
             varselId = dbVarsel.varselId,
             ident = dbVarsel.ident,
             sensitivitet = dbVarsel.sensitivitet,
             innhold = dbVarsel.innhold,
             produsent = dbVarsel.produsent,
             eksternVarslingBestilling = dbVarsel.eksternVarslingBestilling,
             opprettet = dbVarsel.opprettet,
             aktivFremTil = dbVarsel.aktivFremTil,
        )
    }
}
