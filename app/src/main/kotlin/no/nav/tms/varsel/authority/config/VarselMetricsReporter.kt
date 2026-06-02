package no.nav.tms.varsel.authority.config

import io.prometheus.metrics.core.metrics.Counter
import no.nav.tms.token.support.user.token.verification.LevelOfAssurance
import no.nav.tms.varsel.action.Produsent
import no.nav.tms.varsel.action.ValidationError
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import no.nav.tms.varsel.action.Varseltype

object VarselMetricsReporter {

    const val NAMESPACE = "tms_varsel_authority_v1"

    private const val VARSEL_AKTIVERT_NAME = "${NAMESPACE}_varsel_aktivert"
    private const val VARSEL_INAKTIVERT_NAME = "${NAMESPACE}_varsel_inaktivert"
    private const val VARSEL_ARKIVERT_NAME = "${NAMESPACE}_varsel_arkivert"
    private const val VARSEL_INVALID_NAME = "${NAMESPACE}_varsel_invalid"
    private const val VARSEL_API_KALL = "${NAMESPACE}_varsel_api_kall"


    private val VARSEL_AKTIVERT: Counter = Counter.builder()
        .name(VARSEL_AKTIVERT_NAME)
        .help("Varsler aktivert")
        .labelNames("type", "produsent_cluster","produsent_namespace", "produsent_app")
        .register()

    private val VARSEL_INAKTIVERT: Counter = Counter.builder()
        .name(VARSEL_INAKTIVERT_NAME)
        .help("Varsler inaktivert")
        .labelNames("type", "produsent_cluster", "produsent_namespace", "produsent_app", "kilde")
        .register()

    private val VARSEL_ARKIVERT: Counter = Counter.builder()
        .name(VARSEL_ARKIVERT_NAME)
        .help("Varsler arkivert")
        .labelNames("type", "produsent_namespace", "produsent_app")
        .register()

    private val VARSEL_INVALID: Counter = Counter.builder()
        .name(VARSEL_INVALID_NAME)
        .help("Varsler med feilaktig innhold")
        .labelNames("type", "produsent_cluster", "produsent_namespace", "produsent_app", "feil")
        .register()

    fun registerVarselAktivert(varseltype: Varseltype, produsent: DatabaseProdusent) {
        VARSEL_AKTIVERT
            .labelValues(varseltype.name.lowercase(), produsent.cluster ?: "null", produsent.namespace, produsent.appnavn)
            .inc()
    }

    fun registerVarselInaktivert(varseltype: Varseltype, produsent: DatabaseProdusent, kilde: VarselInaktivertKilde) {
        VARSEL_INAKTIVERT
            .labelValues(varseltype.name.lowercase(), produsent.cluster ?: "null", produsent.namespace, produsent.appnavn, kilde.lowercaseName)
            .inc()
    }

    fun registerVarselArkivert(varseltype: Varseltype, produsent: DatabaseProdusent) {
        VARSEL_ARKIVERT
            .labelValues(varseltype.name.lowercase(), produsent.namespace, produsent.appnavn)
            .inc()
    }

    fun registrerVarselInvalid(varseltype: Varseltype, produsent: Produsent, validationErrors: List<ValidationError>) {
        val errorLabels = validationErrors.map { it.title }
            .sorted()
            .joinToString(separator = ",")

        VARSEL_INVALID
            .labelValues(varseltype.name.lowercase(), produsent.cluster, produsent.namespace, produsent.appnavn, errorLabels)
            .inc()
    }

    private val VARSEL_HENTET: Counter = Counter.builder()
        .name(VARSEL_API_KALL)
        .help("Varsler hentet for lesing")
        .labelNames("type", "source", "assurance_level")
        .register()

    fun registerVarselHentet(varseltype: Varseltype?, source: Source, levelOfAssurance: LevelOfAssurance? = null) {
        VARSEL_HENTET
            .labelValues(
                varseltype?.name?.lowercase() ?: "all",
                source.lowercaseName,
                levelOfAssurance?.name?.lowercase() ?: "na"
            )
            .inc()
    }
}

enum class Source {
    BRUKER, SAKSBEHANDLER, ADMIN;
    val lowercaseName = name.lowercase()
}
