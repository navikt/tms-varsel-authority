package no.nav.tms.varsel.builder

import java.time.ZonedDateTime

object VarselBuilder {
    fun opprett(type: VarselType) = OpprettVarselInstance(type)

    fun inaktiver() = InaktiverVarselInstance()

    class OpprettVarselInstance(
        var type: VarselType? = null,
        var varselId: String? = null,
        var ident: String? = null,
        var sensitivitet: Sensitivitet? = null,
        var lenke: String? = null,
        val tekster: List<Tekst> = mutableListOf(),
        var eksternVarsling: EksternVarsling? = null,
        var aktivFremTil: ZonedDateTime? = null
    ) {

    }

    class InaktiverVarselInstance(
        var varselId: String? = null,
        var metadata: Metadata? = null
    )
}
