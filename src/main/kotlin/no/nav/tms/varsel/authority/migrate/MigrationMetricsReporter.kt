package no.nav.tms.varsel.authority.migrate

import io.prometheus.client.Counter
import no.nav.tms.varsel.authority.VarselType

object MigrationMetricsReporter {

    private const val NAMESPACE = "tms_varsel_authority_v1"

    private const val VARSEL_MIGRERT_NAME = "varsel_migrert"
    private const val ARKIVERT_VARSEL_MIGRERT_NAME = "arkivert_varsel_migrert"

    private const val VARSEL_MIGRERT_DUPLIKAT_NAME = "varsel_migrert_duplikat"
    private const val ARKIVERT_VARSEL_MIGRERT_DUPLIKAT_NAME = "arkivert_varsel_migrert_duplikat"


    private val VARSEL_MIGRERT: Counter = Counter.build()
        .name(VARSEL_MIGRERT_NAME)
        .namespace(NAMESPACE)
        .help("Antall varsler migrert")
        .labelNames("type")
        .register()

    private val ARKIVERT_VARSEL_MIGRERT: Counter = Counter.build()
        .name(ARKIVERT_VARSEL_MIGRERT_NAME)
        .namespace(NAMESPACE)
        .help("Antall arkiverte varsler migrert")
        .labelNames("type")
        .register()

    private val VARSEL_MIGRERT_DUPLIKAT: Counter = Counter.build()
        .name(VARSEL_MIGRERT_DUPLIKAT_NAME)
        .namespace(NAMESPACE)
        .help("Antall migrerte varsler - duplikat")
        .labelNames("type")
        .register()

    private val ARKIVERT_VARSEL_MIGRERT_DUPLIKAT: Counter = Counter.build()
        .name(ARKIVERT_VARSEL_MIGRERT_DUPLIKAT_NAME)
        .namespace(NAMESPACE)
        .help("Antall arkiverte varsler migrert - duplikat")
        .labelNames("type")
        .register()


    fun registerVarselMigrert(varselType: VarselType, migrert: Int, duplikat: Int) {
        VARSEL_MIGRERT
            .labels(varselType.lowercaseName)
            .inc(migrert.toDouble())

        VARSEL_MIGRERT_DUPLIKAT
            .labels(varselType.lowercaseName)
            .inc(duplikat.toDouble())
    }


    fun registerArkivertVarselMigrert(varselType: VarselType, migrert: Int, duplikat: Int) {
        ARKIVERT_VARSEL_MIGRERT
            .labels(varselType.lowercaseName)
            .inc(migrert.toDouble())

        ARKIVERT_VARSEL_MIGRERT_DUPLIKAT
            .labels(varselType.lowercaseName)
            .inc(duplikat.toDouble())
    }
}
