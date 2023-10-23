package no.nav.tms.varsel.builder

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.tms.varsel.action.*
import java.time.ZonedDateTime

object VarselJsonBuilder {
    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun opprett(type: Varseltype, builderFunction: OpprettVarselInstance.() -> Unit): String {
        val builder = OpprettVarselInstance(type)
            .also { it.builderFunction() }
            .also { it.performNullCheck() }

        return builder.build()
            .also { OpprettVarselValidation.validate(it) }
            .let { objectMapper.writeValueAsString(it) }
    }

    fun inaktiver(builderFunction: InaktiverVarselInstance.() -> Unit): String {
        val builder = InaktiverVarselInstance()
            .also { it.builderFunction() }
            .also { it.performNullCheck() }

        return builder.build()
            .also { InaktiverVarselValidation.validate(it) }
            .let { objectMapper.writeValueAsString(it) }
    }

    class OpprettVarselInstance internal constructor(
        val type: Varseltype,
        var varselId: String? = null,
        var ident: String? = null,
        var sensitivitet: Sensitivitet? = null,
        var link: String? = null,
        val tekster: MutableList<Tekst> = mutableListOf(),
        var eksternVarsling: EksternVarslingBestilling? = null,
        var aktivFremTil: ZonedDateTime? = null,
        var produsent: Produsent? = produsent(),
        var metadata: Metadata = metadata()
    ) {

        fun setTekst(tekst: String, spraakKode: String, default: Boolean = false) {
            tekster += Tekst(spraakKode, tekst, default)
        }

        internal fun build() = OpprettVarsel(
            type = type,
            varselId = varselId!!,
            ident = ident!!,
            sensitivitet = sensitivitet!!,
            link = link,
            tekster = tekster,
            eksternVarsling = eksternVarsling,
            aktivFremTil = aktivFremTil,
            produsent = produsent!!,
            metadata = metadata
        )

        internal fun performNullCheck() {

            requireNotNull(varselId) { "varselId kan ikke være null" }
            requireNotNull(ident) { "ident kan ikke være null" }
            requireNotNull(sensitivitet) { "sensitivitet kan ikke være null" }
            require(tekster.isNotEmpty()) { "Må ha satt minst 1 tekst" }
        }
    }

    class InaktiverVarselInstance internal constructor(
        var varselId: String? = null,
        var produsent: Produsent? = produsent(),
        var metadata: Metadata = metadata()
    ) {
        internal fun build() = InaktiverVarsel(
            varselId = varselId!!,
            produsent = produsent!!,
            metadata = metadata
        )

        internal fun performNullCheck() {
            requireNotNull(varselId) { "varselId kan ikke være null" }
        }
    }

    private fun produsent(): Produsent? {
        val cluster: String? = System.getenv("NAIS_CLUSTER_NAME")
        val namespace: String? = System.getenv("NAIS_NAMESPACE")
        val appnavn: String? = System.getenv("NAIS_APP_NAME")

        return if (cluster.isNullOrBlank() || namespace.isNullOrBlank() || appnavn.isNullOrBlank()) {
            null
        } else {
            Produsent(
                cluster = cluster,
                namespace = namespace,
                appnavn = appnavn
            )
        }
    }

    private fun metadata() = Metadata(
            versjon = VarselActionVersion,
            byggetid = ZonedDateTime.now()
        )
}
