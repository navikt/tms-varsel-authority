package no.nav.tms.varsel.authority.write.expired

import no.nav.tms.varsel.authority.write.sink.Produsent
import no.nav.tms.varsel.authority.write.sink.VarselType

data class ExpiredVarsel(
    val varselId: String,
    val varselType: VarselType,
    val namespace: String,
    val appnavn: String
) {
    val produsent get() = Produsent(namespace, appnavn)
}
