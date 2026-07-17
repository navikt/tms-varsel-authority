package no.nav.tms.varsel.authority

import kotlinx.coroutines.runBlocking
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.common.postgres.Postgres
import no.nav.tms.kafka.application.Domain
import no.nav.tms.kafka.application.KafkaApplication
import no.nav.tms.kafka.producer.KafkaProducerBuilder
import no.nav.tms.varsel.authority.config.Environment
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.write.outgoing.RecordQueueRepository
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
import no.nav.tms.varsel.authority.write.outgoing.PeriodicKafkaQueueProcessor
import org.flywaydb.core.Flyway

fun main() {
    val environment = Environment()
    val database = Postgres.connectToJdbcUrl(environment.jdbcUrl)
    val varselRepository = WriteVarselRepository(database)

    val eksternVarslingStatusRepository = EksternVarslingStatusRepository(database)
    val eksternVarslingStatusUpdater = EksternVarslingStatusUpdater(
        eksternVarslingStatusRepository,
        varselRepository
    )

    val leaderElection = PodLeaderElection()
    val recordQueueRepository = RecordQueueRepository(database)

    val kafkaQueueProcessor = PeriodicKafkaQueueProcessor(
        repository = recordQueueRepository,
        recordProducer = KafkaProducerBuilder.stringProducer(),
        leaderElection = leaderElection,
    )

    val varselOpprettetProducer = VarselOpprettetProducer(
        queueRepository = recordQueueRepository,
        topicName = environment.internalVarselTopic,
    )

    val varselInaktivertProducer = VarselInaktivertProducer(
        queueRepository = recordQueueRepository,
        topicName = environment.internalVarselTopic,
    )

    val expiredVarselRepository = ExpiredVarselRepository(database)
    val periodicExpiredVarselProcessor =
        PeriodicExpiredVarselProcessor(expiredVarselRepository, varselInaktivertProducer, leaderElection)

    val varselArkivertProducer = VarselArkivertProducer(
        queueRepository = recordQueueRepository,
        topicName = environment.internalVarselTopic
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

        healthCheck("VarselExpiryProcessor", periodicExpiredVarselProcessor::isHealthy)
        healthCheck("KafkaRecordProcessor", kafkaQueueProcessor::isHealthy)

        onStartup {
            Flyway.configure()
                .dataSource(database.dataSource)
                .load()
                .migrate()
        }

        onReady {
            periodicExpiredVarselProcessor.start()
            varselArchiver.start()
            kafkaQueueProcessor.start()
        }

        onShutdown {
            runBlocking {
                periodicExpiredVarselProcessor.stop()
                varselArchiver.stop()
                kafkaQueueProcessor.stop()
                kafkaQueueProcessor.flushAndClose()
            }
        }

        minSideMdc {
            domain = Domain.varsel
            idFieldName = "varselId"
            producedBySupplier { message ->
                val produsentNode = message.json.get("produsent")

                if (produsentNode?.isObject == true) {
                    val cluster = produsentNode["cluster"].asText()
                    val namespace = produsentNode["namespace"].asText()
                    val appnavn = produsentNode["appnavn"].asText()

                    "$cluster:$namespace:$appnavn"
                } else {
                    null
                }
            }
        }
    }.start()
}
