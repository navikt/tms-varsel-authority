package no.nav.tms.varsel.authority.config

import io.prometheus.client.Counter
import no.nav.tms.token.support.tokenx.validation.LevelOfAssurance
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import no.nav.tms.varsel.action.Varseltype

object VarselMetricsReporter {

    private const val NAMESPACE = "tms_varsel_authority_v1"

    private const val VARSEL_AKTIVERT_NAME = "varsel_aktivert"
    private const val VARSEL_INAKTIVERT_NAME = "varsel_inaktivert"
    private const val VARSEL_ARKIVERT_NAME = "varsel_arkivert"
    private const val EKSTERN_VARSLING_SENDT_NAME = "ekstern_varsling_sendt"
    private const val VARSEL_API_KALL = "varsel_api_kall"


    private val VARSEL_AKTIVERT: Counter = Counter.build()
        .name(VARSEL_AKTIVERT_NAME)
        .namespace(NAMESPACE)
        .help("Varsler aktivert")
        .labelNames("type", "produsent_cluster","produsent_namespace", "produsent_app", "source_topic")
        .register()

    private val VARSEL_INAKTIVERT: Counter = Counter.build()
        .name(VARSEL_INAKTIVERT_NAME)
        .namespace(NAMESPACE)
        .help("Varsler inaktivert")
        .labelNames("type", "produsent_cluster", "produsent_namespace", "produsent_app", "kilde", "source_topic")
        .register()

    private val VARSEL_ARKIVERT: Counter = Counter.build()
        .name(VARSEL_ARKIVERT_NAME)
        .namespace(NAMESPACE)
        .help("Varsler arkivert")
        .labelNames("type", "produsent_namespace", "produsent_app")
        .register()

    private val EKSTERN_VARSLING_SENDT: Counter = Counter.build()
        .name(EKSTERN_VARSLING_SENDT_NAME)
        .namespace(NAMESPACE)
        .help("Eksterne varsler sendt")
        .labelNames("type", "produsent_namespace", "produsent_app", "kanal")
        .register()

    fun registerVarselAktivert(varseltype: Varseltype, produsent: DatabaseProdusent, sourceTopic: String) {
        VARSEL_AKTIVERT
            .labels(varseltype.name.lowercase(), produsent.cluster ?: "null", produsent.namespace, produsent.appnavn, sourceTopic)
            .inc()
    }

    fun registerVarselInaktivert(varseltype: Varseltype, produsent: DatabaseProdusent, kilde: VarselInaktivertKilde, sourceTopic: String) {
        VARSEL_INAKTIVERT
            .labels(varseltype.name.lowercase(), produsent.cluster ?: "null", produsent.namespace, produsent.appnavn, kilde.lowercaseName, sourceTopic)
            .inc()
    }

    fun registerVarselArkivert(varseltype: Varseltype, produsent: DatabaseProdusent) {
        VARSEL_ARKIVERT
            .labels(varseltype.name.lowercase(), produsent.namespace, produsent.appnavn)
            .inc()
    }

    fun registerEksternVarslingSendt(varseltype: Varseltype, produsent: DatabaseProdusent, kanal: String) {
        EKSTERN_VARSLING_SENDT
            .labels(varseltype.name.lowercase(), produsent.namespace, produsent.appnavn, kanal)
            .inc()
    }

    private val VARSEL_HENTET: Counter = Counter.build()
        .name(VARSEL_API_KALL)
        .namespace(NAMESPACE)
        .help("Varsler hentet for lesing")
        .labelNames("type", "source", "assurance_level")
        .register()

    fun registerVarselHentet(varseltype: Varseltype?, source: Source, levelOfAssurance: LevelOfAssurance?=null) {
        VARSEL_HENTET
            .labels(
                varseltype?.name?.lowercase() ?: "all",
                source.lowercaseName,
                levelOfAssurance?.name?.lowercase() ?: "na"
            )
            .inc()
    }
}

enum class Source {
    BRUKER, SAKSBEHANDLER;
    val lowercaseName = name.lowercase()
}
