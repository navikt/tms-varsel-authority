package no.nav.tms.varsel.builder

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
    val type: VarselType,
    val varselId: String,
    val ident: String,
    val sensitivitet: Sensitivitet,
    val innhold: Innhold,
    val eksternVarsling: EksternVarsling? = null,
    val aktivFremTil: ZonedDateTime? = null,
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Opprett
}

data class EndreVarsel(
    val varselId: String,
    val innhold: Innhold,
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Endre
}

data class InaktiverVarsel(
    val varselId: String,
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Inaktiver
}

data class SlettVarsel(
    val varselId: String,
    val metadata: Metadata
) {
    @JsonProperty("@event_name") val eventName = EventType.Slett
}

data class Innhold(
    val tekster: List<Tekst>,
    val lenke: String?
)

data class Tekst(
    val spraakKode: String,
    val tekst: String,
    val default: Boolean
)

enum class VarselType {
    Beskjed, Oppgave, Innboks;

    private val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName
}

enum class Sensitivitet {
    Substantial,
    High;

    private val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName
}

data class Metadata(
    val version: String,
    val produsent: Produsent
)

data class Produsent(
    val namespace: String,
    val appnavn: String
)

enum class EksternKanal {
    SMS, EPOST
}

data class EksternVarsling(
    val prefererteKanaler: List<EksternKanal>,
    val smsVarslingstekst: String?,
    val epostVarslingstekst: String?,
    val epostVarslingstittel: String?,
)
