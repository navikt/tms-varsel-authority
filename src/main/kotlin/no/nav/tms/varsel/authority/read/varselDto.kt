package no.nav.tms.varsel.authority.read

import no.nav.tms.varsel.authority.EksternVarslingStatus
import no.nav.tms.varsel.authority.Produsent
import java.time.ZonedDateTime

data class Varselsammendrag(
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

data class DetaljertVarsel(
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
    val inaktivertAv: String?
)
