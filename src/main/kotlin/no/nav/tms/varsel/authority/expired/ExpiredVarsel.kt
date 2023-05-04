package no.nav.tms.varsel.authority.expired

data class ExpiredVarsel(
    val varselId: String,
    val varselType: String,
    val namespace: String,
    val appnavn: String
)
