package no.nav.tms.varsel.action

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.time.ZonedDateTime

enum class EventType {
    Opprett, Endre, Inaktiver, Slett;

    private val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName
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
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Opprett
}

data class InaktiverVarsel(
    val varselId: String,
    val produsent: Produsent,
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Inaktiver
}

data class Tekst(
    val spraakKode: String,
    val tekst: String,
    val default: Boolean
)

enum class Varseltype {
    Beskjed, Oppgave, Innboks;

    val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName
}

enum class Sensitivitet {
    Substantial,
    High;

    val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName
}

data class Metadata(
    val versjon: String?,
    val byggetid: ZonedDateTime?
)

data class Produsent(
    val cluster: String,
    val namespace: String,
    val appnavn: String
)

enum class EksternKanal {
    SMS, EPOST
}

data class EksternVarslingBestilling(
    val prefererteKanaler: List<EksternKanal>,
    val smsVarslingstekst: String?,
    val epostVarslingstekst: String?,
    val epostVarslingstittel: String?,
)

internal data class EndreVarsel(
    val varselId: String,
    val link: String?,
    val tekst: String,
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Endre
}

internal data class SlettVarsel(
    val varselId: String,
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Slett
}
