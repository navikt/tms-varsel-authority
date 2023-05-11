package no.nav.tms.varsel.authority.read

import no.nav.tms.varsel.authority.write.sink.EksternVarslingStatus
import no.nav.tms.varsel.authority.write.sink.Produsent
import java.time.ZonedDateTime

data class AbreviatedVarsel(
    val type: String,
    val varselId: String,
    val aktiv: Boolean,
    val tekst: String,
    val link: String,
    val sikkerhetsnivaa: Int,
    val eksternVarslingSendt: Boolean,
    val eksternVarslingKanaler: List<String>,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?,
    val fristUtløpt: Boolean?
)

data class FullVarsel(
    val type: String,
    val varselId: String,
    val aktiv: Boolean,
    val produsent: Produsent,
    val tekst: String,
    val link: String,
    val sikkerhetsnivaa: Int,
    val eksternVarsling: EksternVarslingStatus?,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?,
    val inaktivertAv: String
)
