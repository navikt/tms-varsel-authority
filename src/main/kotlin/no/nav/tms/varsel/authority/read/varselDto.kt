package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.tms.varsel.authority.EksternVarslingStatus
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.Produsent
import no.nav.tms.varsel.authority.Sensitivitet
import java.time.ZonedDateTime

data class Varselsammendrag(
    val type: String,
    val varselId: String,
    val aktiv: Boolean,
    @JsonIgnore val sensitivitet: Sensitivitet,
    val innhold: Innhold?,
    val eksternVarslingSendt: Boolean,
    val eksternVarslingKanaler: List<String>,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?
)

data class DetaljertVarsel(
    val type: String,
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
