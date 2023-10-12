package no.nav.tms.varsel.builder

import java.time.ZonedDateTime

object VarselBuilder {
    fun opprett(type: VarselType, builderFunction: OpprettVarselInstance.() -> Unit): OpprettVarsel {
        val builder = OpprettVarselInstance(type)
            .also { it.builderFunction() }
            .also { it.performNullCheck() }

        return builder.build()
            .also { validate(it) }
    }

    fun inaktiver() = InaktiverVarselInstance()

    class OpprettVarselInstance internal constructor(
        val type: VarselType,
        var varselId: String? = null,
        var ident: String? = null,
        var sensitivitet: Sensitivitet? = null,
        var lenke: String? = null,
        val tekster: List<Tekst> = mutableListOf(),
        var eksternVarsling: EksternVarsling? = null,
        var aktivFremTil: ZonedDateTime? = null,
        var metadata: Metadata? = null
    ) {
        internal fun build() = OpprettVarsel(
            type = type,
            varselId = varselId!!,
            ident = ident!!,
            sensitivitet = sensitivitet!!,
            innhold = Innhold(tekster, lenke),
            eksternVarsling = eksternVarsling,
            aktivFremTil = aktivFremTil,
            metadata = metadata!!,
        )
    }

    class InaktiverVarselInstance internal constructor(
        var varselId: String? = null,
        var metadata: Metadata? = null
    )
}
