package no.nav.tms.varsel.authority.write.expiry

import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.Produsent

data class ExpiredVarsel(
    val varselId: String,
    val varselType: VarselType,
    val namespace: String,
    val appnavn: String
) {
    val produsent get() = Produsent(namespace, appnavn)
}
