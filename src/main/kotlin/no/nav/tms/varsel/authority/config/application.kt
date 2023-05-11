package no.nav.tms.varsel.authority.config

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidApplication.RapidApplicationConfig.Companion.fromEnv
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.tms.varsel.authority.write.archive.PeriodicVarselArchiver
import no.nav.tms.varsel.authority.write.archive.VarselArchiveRepository
import no.nav.tms.varsel.authority.write.archive.VarselArkivertProducer
import no.nav.tms.varsel.authority.common.database.Database
import no.nav.tms.varsel.authority.write.eksternvarsling.EksternVarslingOppdatertProducer
import no.nav.tms.varsel.authority.write.done.VarselInaktivertProducer
import no.nav.tms.varsel.authority.election.LeaderElection
import no.nav.tms.varsel.authority.metrics.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.expired.ExpiredVarselRepository
import no.nav.tms.varsel.authority.write.expired.PeriodicExpiredVarselProcessor
import no.nav.tms.varsel.authority.write.sink.WriteVarselRepository
import no.nav.tms.varsel.authority.varsel.VarselAktivertProducer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

fun main() {
    val environment = Environment()
    val database: Database = PostgresDatabase(environment)

    startRapid(environment, database)
}

private fun startRapid(environment: Environment, database: Database) {
    val varselRepository = WriteVarselRepository(database)
    val eksternVarslingOppdatertProducer = EksternVarslingOppdatertProducer(
        kafkaProducer = initializeRapidKafkaProducer(environment),
        topicName = environment.rapidTopic,
    )

//    val eksternVarslingStatusUpdater = EksternVarslingStatusUpdater(eksternVarslingStatusRepository, varselRepository, eksternVarslingOppdatertProducer)

    val varselAktivertProducer = VarselAktivertProducer(
        kafkaProducer = initializeRapidKafkaProducer(environment),
        topicName = environment.rapidTopic,
    )

    val varselInaktivertProducer = VarselInaktivertProducer(
        kafkaProducer = initializeRapidKafkaProducer(environment),
        topicName = environment.rapidTopic,
    )

    val leaderElection = LeaderElection()
    val prometheusMetricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val metricsReporter = VarselMetricsReporter(prometheusMetricsRegistry)

    val expiredVarselRepository = ExpiredVarselRepository(database)
    val periodicExpiredVarselProcessor =
        PeriodicExpiredVarselProcessor(expiredVarselRepository, varselInaktivertProducer, leaderElection, metricsReporter)

    val varselArkivertProducer = VarselArkivertProducer(
        initializeRapidKafkaProducer(environment),
        environment.rapidTopic
    )
    val varselArchivingRepository = VarselArchiveRepository(database)

    val varselArchiver = PeriodicVarselArchiver(varselArchivingRepository, varselArkivertProducer, environment.archivingThresholdDays, leaderElection, metricsReporter)

    RapidApplication.Builder(fromEnv(environment.rapidConfig())).withKtorModule {
//        doneApi(
//            beskjedRepository = BeskjedRepository(database = database),
//            producer = varselInaktivertProducer
//        )

    }.build().apply {
//        BeskjedSink(
//            rapidsConnection = this,
//            varselRepository = varselRepository,
//            varselAktivertProducer = varselAktivertProducer,
//            rapidMetricsProbe = rapidMetricsProbe
//        )
//        DoneSink(
//            rapidsConnection = this,
//            varselRepository = varselRepository,
//            varselInaktivertProducer = varselInaktivertProducer,
//            rapidMetricsProbe = rapidMetricsProbe
//        )
//        EksternVarslingStatusSink(
//            rapidsConnection = this,
//            eksternVarslingStatusUpdater = eksternVarslingStatusUpdater
//        )
    }.apply {
        register(object : RapidsConnection.StatusListener {
            override fun onStartup(rapidsConnection: RapidsConnection) {
                Flyway.runFlywayMigrations(environment)
                periodicExpiredVarselProcessor.start()
                varselArchiver.start()
            }

            override fun onShutdown(rapidsConnection: RapidsConnection) {
                runBlocking {
                    periodicExpiredVarselProcessor.stop()
                    varselArchiver.stop()
                    varselInaktivertProducer.flushAndClose()
                    varselAktivertProducer.flushAndClose()
                }
            }
        })
    }.start()
}


private fun initializeRapidKafkaProducer(environment: Environment) = KafkaProducer<String, String>(
    Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.aivenBrokers)
        put(
            ProducerConfig.CLIENT_ID_CONFIG,
            "tms-varsel-authority"
        )
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 40000)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
        environment.securityConfig.variables.also { securityVars ->
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, securityVars.aivenTruststorePath)
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, securityVars.aivenKeystorePath)
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, securityVars.aivenCredstorePassword)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        }
    }
)
