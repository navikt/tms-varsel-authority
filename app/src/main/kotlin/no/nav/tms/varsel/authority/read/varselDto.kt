package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import java.time.ZonedDateTime

private const val defaultSpraakkode = "nb"

data class DatabaseVarselsammendrag(
    val type: Varseltype,
    val varselId: String,
    val aktiv: Boolean,
    val innhold: Innhold,
    val eksternVarslingSendt: Boolean,
    val eksternVarslingKanaler: List<String>,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?,
    val sensitivitet: Sensitivitet
) {
    fun tekstOrDefault(spraakkode: String?): Tekst {
        return if (innhold.tekster.isEmpty()) {
            Tekst(
                spraakkode = defaultSpraakkode,
                tekst = innhold.tekst,
                default = true
            )
        } else if (innhold.tekster.size == 1) {
            innhold.tekster.first()
        } else {
            innhold.tekster.find { it.spraakkode == spraakkode }
                ?: innhold.tekster.first { it.default }
        }
    }
}

data class Varselsammendrag(
    val type: Varseltype,
    val varselId: String,
    val aktiv: Boolean,
    val innhold: Innholdsammendrag?,
    val eksternVarslingSendt: Boolean,
    val eksternVarslingKanaler: List<String>,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?,
)

data class Innholdsammendrag(
    val spraakkode: String,
    val tekst: String,
    val link: String?
)

data class DetaljertVarsel(
    val type: Varseltype,
    val varselId: String,
    val aktiv: Boolean,
    val produsent: DatabaseProdusent,
    val sensitivitet: Sensitivitet,
    val innhold: Innhold,
    val eksternVarsling: EksternVarslingStatus?,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime?,
    val inaktivert: ZonedDateTime?,
    val inaktivertAv: String?
)
