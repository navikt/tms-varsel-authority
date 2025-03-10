package no.nav.tms.varsel.action

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.time.ZonedDateTime

enum class EventType {
    Opprett, Endre, Inaktiver, Slett;

    @JsonValue
    fun toJson() = name.lowercase()
}

data class OpprettVarsel(
    val type: Varseltype,
    val varselId: String,
    val ident: String,
    val sensitivitet: Sensitivitet,
    val link: String?,
    val tekster: List<Tekst>,
    val eksternVarsling: EksternVarslingBestilling? = null,
    val aktivFremTil: ZonedDateTime? = null,
    val produsent: Produsent,
    val metadata: Map<String, Any>?
) {
    @JsonProperty("@event_name") val eventName = EventType.Opprett
}

data class InaktiverVarsel(
    val varselId: String,
    val produsent: Produsent,
    val metadata: Map<String, Any>?
) {
    @JsonProperty("@event_name") val eventName = EventType.Inaktiver
}

data class Tekst(
    val spraakkode: String,
    val tekst: String,
    val default: Boolean
)

enum class Varseltype {
    Beskjed, Oppgave, Innboks;

    @JsonValue
    fun toJson() = name.lowercase()
}

enum class Sensitivitet {
    Substantial,
    High;

    @JsonValue
    fun toJson() = name.lowercase()
}

data class Produsent(
    val cluster: String,
    val namespace: String,
    val appnavn: String
)

enum class EksternKanal {
    SMS, EPOST, BETINGET_SMS
}

data class EksternVarslingBestilling(
    val prefererteKanaler: List<EksternKanal> = emptyList(),
    val smsVarslingstekst: String? = null,
    val epostVarslingstittel: String? = null,
    val epostVarslingstekst: String? = null,
    val kanBatches: Boolean? = null,
    val utsettSendingTil: ZonedDateTime? = null,
)

internal data class EndreVarsel(
    val varselId: String,
    val link: String?,
    val tekst: String,
    val metadata: Map<String, Any>?
) {
    @JsonProperty("@event_name") val eventName = EventType.Endre
}

internal data class SlettVarsel(
    val varselId: String,
    val metadata: Map<String, Any>?
) {
    @JsonProperty("@event_name") val eventName = EventType.Slett
}
