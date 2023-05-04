package no.nav.tms.varsel.authority.varsel

data class VarselHeader(
        val eventId: String,
        val type: VarselType,
        val fodselsnummer: String,
        val namespace: String,
        val appnavn: String
)
