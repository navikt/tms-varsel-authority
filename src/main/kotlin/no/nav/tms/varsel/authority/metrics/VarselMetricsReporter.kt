package no.nav.tms.varsel.authority.metrics

import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Counter
import no.nav.tms.varsel.authority.write.done.VarselInaktivertKilde
import no.nav.tms.varsel.authority.write.sink.Produsent
import no.nav.tms.varsel.authority.write.sink.VarselType

class VarselMetricsReporter(private val prometheusMeterRegistry: PrometheusMeterRegistry) {

    private val NAMESPACE = "tms.varsel.authority.v1"

    private val VARSEL_AKTIVERT_NAME = "$NAMESPACE.varsel.aktivert"
    private val VARSEL_INAKTIVERT_NAME = "$NAMESPACE.varsel.inaktivert"
    private val VARSEL_ARKIVERT_NAME = "$NAMESPACE.varsel.arkivert"

    private val EKSTERN_VARSLING_SENDT_NAME = "$NAMESPACE.ekstern.varsling.sendt"

    fun registerVarselAktivert(varselType: VarselType, produsent: Produsent) {
        prometheusMeterRegistry.counter(VARSEL_AKTIVERT_NAME,
            tags(
                "type" to varselType.lowercaseName,
                "produsent_namespace" to produsent.namespace,
                "produsent_app" to produsent.appnavn
            )
        ).increment()
    }

    fun registerVarselInaktivert(varselType: VarselType, produsent: Produsent, kilde: VarselInaktivertKilde) {
        prometheusMeterRegistry.counter(VARSEL_INAKTIVERT_NAME,
            tags(
                "type" to varselType.lowercaseName,
                "produsent_namespace" to produsent.namespace,
                "produsent_app" to produsent.appnavn,
                "kilde" to kilde.lowercaseName
            )
        ).increment()
    }

    fun registerVarselArkivert(varselType: VarselType, produsent: Produsent) {
        prometheusMeterRegistry.counter(VARSEL_ARKIVERT_NAME,
            tags(
                "type" to varselType.lowercaseName,
                "produsent_namespace" to produsent.namespace,
                "produsent_app" to produsent.appnavn
            )
        ).increment()
    }

    fun registerEksternVarslingSendt(varselType: VarselType, produsent: Produsent, kanal: String) {
        prometheusMeterRegistry.counter(EKSTERN_VARSLING_SENDT_NAME,
            tags(
                "type" to varselType.lowercaseName,
                "produsent_namespace" to produsent.namespace,
                "produsent_app" to produsent.appnavn,
                "kanal" to kanal
            )
        ).increment()
    }

    private fun tags(vararg tagPairs: Pair<String, String>): List<Tag> {
        return tagPairs.map { (k, v) -> TagImpl(k, v)}
    }

    private class TagImpl(
        private val key: String,
        private val value: String
    ): Tag {
        override fun getKey() = key
        override fun getValue() = value
    }
}
