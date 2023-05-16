package no.nav.tms.varsel.authority

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidApplication.RapidApplicationConfig.Companion.fromEnv
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.config.Environment
import no.nav.tms.varsel.authority.config.Flyway
import no.nav.tms.varsel.authority.config.PostgresDatabase
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.archive.PeriodicVarselArchiver
import no.nav.tms.varsel.authority.write.archive.VarselArchiveRepository
import no.nav.tms.varsel.authority.write.archive.VarselArkivertProducer
import no.nav.tms.varsel.authority.write.eksternvarsling.EksternVarslingOppdatertProducer
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.election.LeaderElection
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.write.aktiver.AktiverVarselSink
import no.nav.tms.varsel.authority.write.expiry.ExpiredVarselRepository
import no.nav.tms.varsel.authority.write.expiry.PeriodicExpiredVarselProcessor
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository
import no.nav.tms.varsel.authority.write.aktiver.VarselAktivertProducer
import no.nav.tms.varsel.authority.write.eksternvarsling.EksternVarslingStatusRepository
import no.nav.tms.varsel.authority.write.eksternvarsling.EksternVarslingStatusSink
import no.nav.tms.varsel.authority.write.eksternvarsling.EksternVarslingStatusUpdater
import no.nav.tms.varsel.authority.write.inaktiver.BeskjedInaktiverer
import no.nav.tms.varsel.authority.write.inaktiver.BeskjedInaktivertAvBrukerSink
import no.nav.tms.varsel.authority.write.inaktiver.InaktiverVarselSink
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

    val prometheusMetricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val metricsReporter = VarselMetricsReporter(prometheusMetricsRegistry)

    val varselRepository = WriteVarselRepository(database)
    val eksternVarslingOppdatertProducer = EksternVarslingOppdatertProducer(
        kafkaProducer = initializeRapidKafkaProducer(environment),
        topicName = environment.internalVarselTopic,
    )

    val eksternVarslingStatusRepository = EksternVarslingStatusRepository(database)
    val eksternVarslingStatusUpdater = EksternVarslingStatusUpdater(eksternVarslingStatusRepository, varselRepository, eksternVarslingOppdatertProducer, metricsReporter)

    val varselAktivertProducer = VarselAktivertProducer(
        kafkaProducer = initializeRapidKafkaProducer(environment),
        topicName = environment.internalVarselTopic,
    )

    val varselInaktivertProducer = VarselInaktivertProducer(
        kafkaProducer = initializeRapidKafkaProducer(environment),
        topicName = environment.internalVarselTopic,
    )

    val leaderElection = LeaderElection()

    val expiredVarselRepository = ExpiredVarselRepository(database)
    val periodicExpiredVarselProcessor =
        PeriodicExpiredVarselProcessor(expiredVarselRepository, varselInaktivertProducer, leaderElection, metricsReporter)

    val varselArkivertProducer = VarselArkivertProducer(
        initializeRapidKafkaProducer(environment),
        environment.internalVarselTopic
    )

    val varselArchivingRepository = VarselArchiveRepository(database)

    val varselArchiver = PeriodicVarselArchiver(varselArchivingRepository, varselArkivertProducer, environment.archivingThresholdDays, leaderElection, metricsReporter)

    val readVarselRepository = ReadVarselRepository(database)
    val writeVarselRepository = WriteVarselRepository(database)
    val beskjedService = BeskjedInaktiverer(writeVarselRepository, varselInaktivertProducer, metricsReporter)

    RapidApplication.Builder(fromEnv(environment.rapidConfig)).withKtorModule {
        varselApi(
            readVarselRepository, beskjedService
        )

    }.build().apply {
        AktiverVarselSink(
            rapidsConnection = this,
            varselRepository = varselRepository,
            varselAktivertProducer = varselAktivertProducer,
            metricsReporter = metricsReporter
        )
        InaktiverVarselSink(
            rapidsConnection = this,
            varselRepository = varselRepository,
            varselInaktivertProducer = varselInaktivertProducer,
            metricsReporter = metricsReporter
        )
        BeskjedInaktivertAvBrukerSink(
            rapidsConnection = this,
            varselRepository = varselRepository,
            varselInaktivertProducer = varselInaktivertProducer,
            metricsReporter = metricsReporter
        )
        EksternVarslingStatusSink(
            rapidsConnection = this,
            eksternVarslingStatusUpdater = eksternVarslingStatusUpdater
        )
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
                    varselArkivertProducer.flushAndClose()
                }
            }
        })
    }.start()
}


private fun initializeRapidKafkaProducer(environment: Environment) = KafkaProducer<String, String>(
    Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, environment.kafkaBrokers)
        put(
            ProducerConfig.CLIENT_ID_CONFIG,
            "tms-varsel-authority"
        )
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 40000)
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, environment.kafkaTruststorePath)
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, environment.kafkaCredstorePassword)
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, environment.kafkaKeystorePath)
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, environment.kafkaCredstorePassword)
            put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, environment.kafkaCredstorePassword)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
    }
)
