package no.nav.tms.varsel.authority

import kotlinx.coroutines.runBlocking
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.kafka.application.KafkaApplication
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.config.Environment
import no.nav.tms.varsel.authority.config.Flyway
import no.nav.tms.varsel.authority.config.PostgresDatabase
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.write.arkiv.PeriodicVarselArchiver
import no.nav.tms.varsel.authority.write.arkiv.VarselArkivRepository
import no.nav.tms.varsel.authority.write.arkiv.VarselArkivertProducer
import no.nav.tms.varsel.authority.write.eksternvarsling.*
import no.nav.tms.varsel.authority.write.expiry.ExpiredVarselRepository
import no.nav.tms.varsel.authority.write.expiry.PeriodicExpiredVarselProcessor
import no.nav.tms.varsel.authority.write.inaktiver.InaktiverVarselSubscriber
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktiverer
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.write.opprett.OpprettVarselSubscriber
import no.nav.tms.varsel.authority.write.opprett.VarselOpprettetProducer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
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

    startKafkaApplication(environment, database)
}

private fun startKafkaApplication(environment: Environment, database: Database) {

    val varselRepository = WriteVarselRepository(database)

    val eksternVarslingStatusRepository = EksternVarslingStatusRepository(database)
    val eksternVarslingStatusUpdater = EksternVarslingStatusUpdater(
        eksternVarslingStatusRepository,
        varselRepository
    )

    val varselOpprettetProducer = VarselOpprettetProducer(
        kafkaProducer = initializeKafkaProducer(environment),
        topicName = environment.internalVarselTopic,
    )

    val varselInaktivertProducer = VarselInaktivertProducer(
        kafkaProducer = initializeKafkaProducer(environment),
        topicName = environment.internalVarselTopic,
    )

    val leaderElection = PodLeaderElection()

    val expiredVarselRepository = ExpiredVarselRepository(database)
    val periodicExpiredVarselProcessor =
        PeriodicExpiredVarselProcessor(expiredVarselRepository, varselInaktivertProducer, leaderElection)

    val varselArkivertProducer = VarselArkivertProducer(
        initializeKafkaProducer(environment),
        environment.internalVarselTopic
    )

    val varselArchivingRepository = VarselArkivRepository(database)

    val varselArchiver = PeriodicVarselArchiver(
        varselArchivingRepository,
        varselArkivertProducer,
        environment.archivingThresholdDays,
        leaderElection
    )

    val readVarselRepository = ReadVarselRepository(database)
    val writeVarselRepository = WriteVarselRepository(database)
    val varselInaktiverer = VarselInaktiverer(writeVarselRepository, varselInaktivertProducer)
    KafkaApplication.build {
        kafkaConfig {
            groupId = environment.kafkaConsumerGroupId
            readTopics(environment.publicVarselTopic, environment.internalVarselTopic)
        }
        ktorModule {
            varselApi(
                readVarselRepository, varselInaktiverer
            )
        }
        subscribers(
            OpprettVarselSubscriber(
                varselRepository = varselRepository,
                varselAktivertProducer = varselOpprettetProducer
            ),
            InaktiverVarselSubscriber(
                varselRepository = varselRepository,
                varselInaktivertProducer = varselInaktivertProducer
            ),
            EksternVarslingStatusOppdatertSubscriber(
                eksternVarslingStatusUpdater = eksternVarslingStatusUpdater
            )
        )
        onStartup {
            Flyway.runFlywayMigrations(environment)
            periodicExpiredVarselProcessor.start()
            varselArchiver.start()
        }

        onShutdown {
            runBlocking {
                periodicExpiredVarselProcessor.stop()
                varselArchiver.stop()
                varselInaktivertProducer.flushAndClose()
                varselOpprettetProducer.flushAndClose()
                varselArkivertProducer.flushAndClose()
            }
        }
    }.start()
}


private fun initializeKafkaProducer(environment: Environment) = KafkaProducer<String, String>(
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
