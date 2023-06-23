package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.tms.varsel.authority.*
import java.time.ZonedDateTime

data class Varselsammendrag(
    val type: VarselType,
    val varselId: String,
    val aktiv: Boolean,
    val innhold: Innhold?,
    val eksternVarslingSendt: Boolean,
    val eksternVarslingKanaler: List<String>,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?,
    @JsonIgnore val sensitivitet: Sensitivitet? = null
)

data class DetaljertVarsel(
    val type: VarselType,
    val varselId: String,
    val aktiv: Boolean,
    val produsent: Produsent,
    val sensitivitet: Sensitivitet,
    val innhold: Innhold,
    val eksternVarsling: EksternVarslingStatus?,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?,
    val inaktivertAv: String?
)
