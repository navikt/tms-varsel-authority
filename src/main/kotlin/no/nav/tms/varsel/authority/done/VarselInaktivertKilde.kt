package no.nav.tms.varsel.authority.done

enum class VarselInaktivertKilde {
    Bruker, Produsent, Frist;

    val lowercaseName = name.lowercase()

    companion object {
        fun from(string: String): VarselInaktivertKilde {
            return values().filter { it.lowercaseName == string.lowercase() }
                .takeIf { it.size == 1 }
                ?.first()
                ?: throw RuntimeException("No VarselInaktivertKilde enum matches $string")
        }
    }
}
