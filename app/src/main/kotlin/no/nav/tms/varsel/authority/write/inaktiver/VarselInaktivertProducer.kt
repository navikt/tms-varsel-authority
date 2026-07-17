package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.outgoing.RecordQueueRepository
import java.time.ZonedDateTime

class VarselInaktivertProducer(
    private val queueRepository: RecordQueueRepository,
    private val topicName: String,
) {
    private val log = KotlinLogging.logger {}

    private val objectMapper = defaultObjectMapper()

    fun enqueueVarselInaktivert(hendelse: VarselInaktivertHendelse) {

        queueRepository.enqueueRecord(topicName, hendelse.varselId, objectMapper.writeValueAsString(hendelse))

        log.info { "inaktivert-event lagt i record-queue" }
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
    val tidspunkt: ZonedDateTime = nowAtUtc()
}
