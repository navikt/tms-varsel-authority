package no.nav.tms.varsel.authority.common

import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Varseltype


fun parseSensitivitet(string: String): Sensitivitet {
    return Sensitivitet.entries.firstOrNull { it.name.equals(string, ignoreCase = true) }
        ?: throw IllegalArgumentException("Could not parse sensitivitet $string")
}

fun parseVarseltype(string: String): Varseltype {
    return Varseltype.entries.firstOrNull { it.name.equals(string, ignoreCase = true) }
        ?: throw IllegalArgumentException("Could not parse varseltype $string")
}
