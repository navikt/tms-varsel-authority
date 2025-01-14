package no.nav.tms.varsel.builder

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.tms.varsel.action.*
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object VarselActionBuilder {
    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    fun opprett(builderFunction: OpprettVarselInstance.() -> Unit): String {
        val builder = OpprettVarselInstance()
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
            .let { objectMapper.writeValueAsString(it) }
    }

    class OpprettVarselInstance internal constructor(
        var type: Varseltype? = null,
        var varselId: String? = null,
        var ident: String? = null,
        var sensitivitet: Sensitivitet? = null,
        var link: String? = null,
        val tekster: MutableList<Tekst> = mutableListOf(),
        var aktivFremTil: ZonedDateTime? = null,
        var produsent: Produsent? = produsent()
    ) {
        private var eksternVarsling: EksternVarsling? = null

        val metadata = metadata()

        var tekst: Tekst? = null
            set(value) {
                if (value != null && tekst == null) {
                    tekster += value
                } else if (value == null && tekst != null) {
                    tekster.remove(tekst)
                }
                field = value
            }

        fun eksternVarsling(builder: EksternVarsling.() -> Unit) {
            eksternVarsling = EksternVarsling().apply(builder)
        }

        internal fun build() = OpprettVarsel(
            type = type!!,
            varselId = varselId!!,
            ident = ident!!,
            sensitivitet = sensitivitet!!,
            link = link,
            tekster = tekster,
            eksternVarsling = eksternVarsling?.toBestilling(),
            aktivFremTil = aktivFremTil,
            produsent = produsent!!,
            metadata = metadata
        )

        internal fun performNullCheck() {
            try {
                requireNotNull(type) { "type kan ikke være null" }
                requireNotNull(varselId) { "varselId kan ikke være null" }
                requireNotNull(ident) { "ident kan ikke være null" }
                requireNotNull(sensitivitet) { "sensitivitet kan ikke være null" }
                requireNotNull(produsent) { "produsent kan ikke være null" }
                require(tekster.isNotEmpty()) { "Må ha satt minst 1 tekst" }
            } catch (e: IllegalArgumentException) {
                throw VarselValidationException(e.message!!)
            }
        }
    }

    class EksternVarsling internal constructor(
        var preferertKanal: EksternKanal? = null,
        var smsVarslingstekst: String? = null,
        var epostVarslingstittel: String? = null,
        var epostVarslingstekst: String? = null,
        var kanBatches: Boolean? = null,
        var utsettSendingTil: ZonedDateTime? = null,
    ) {
        fun toBestilling() = EksternVarslingBestilling(
            prefererteKanaler = preferertKanal?.let { listOf(it) } ?: emptyList(),
            smsVarslingstekst = smsVarslingstekst,
            epostVarslingstittel = epostVarslingstittel,
            epostVarslingstekst = epostVarslingstekst,
            kanBatches = kanBatches,
            utsettSendingTil = utsettSendingTil,
        )
    }

    class InaktiverVarselInstance internal constructor(
        var varselId: String? = null,
        var produsent: Produsent? = produsent(),
    ) {
        val metadata = metadata()

        internal fun build() = InaktiverVarsel(
            varselId = varselId!!,
            produsent = produsent!!,
            metadata = metadata
        )

        internal fun performNullCheck() = try {
            requireNotNull(varselId) { "varselId kan ikke være null" }
            requireNotNull(produsent) { "produsent kan ikke være null" }
        } catch (e: IllegalArgumentException) {
            throw VarselValidationException(e.message!!)
        }
    }

    private fun produsent(): Produsent? {
        val cluster: String? = BuilderEnvironment.get("NAIS_CLUSTER_NAME")
        val namespace: String? = BuilderEnvironment.get("NAIS_NAMESPACE")
        val appnavn: String? = BuilderEnvironment.get("NAIS_APP_NAME")

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

    private fun metadata() = mutableMapOf<String, Any>(
        "version" to VarselActionVersion,
        "built_at" to ZonedDateTime.now(ZoneId.of("Z")).truncatedTo(ChronoUnit.MILLIS),
        "builder_lang" to "kotlin"
    )
}

object BuilderEnvironment {
    private val baseEnv = System.getenv()
    private val env = mutableMapOf<String, String>()

    init {
        env.putAll(baseEnv)
    }

    fun extend(extendedEnv: Map<String, String>) {
        env.putAll(extendedEnv)
    }

    fun reset() {
        env.clear()
        env.putAll(baseEnv)
    }

    internal fun get(name: String): String? = env[name]
}
