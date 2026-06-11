package no.nav.tms.varsel.authority.write.outgoing

import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.common.logging.TeamLogs
import no.nav.tms.common.util.scheduling.PeriodicJob
import no.nav.tms.kafka.application.AppHealth
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.outgoing.PeriodicKafkaQueueProcessor.Companion.RecordSendStep.Flush
import no.nav.tms.varsel.authority.write.outgoing.PeriodicKafkaQueueProcessor.Companion.RecordSendStep.Result
import no.nav.tms.varsel.authority.write.outgoing.PeriodicKafkaQueueProcessor.Companion.RecordSendStep.Send
import no.nav.tms.varsel.authority.write.outgoing.PeriodicKafkaQueueProcessor.Companion.RecordSendStep.Sync
import org.apache.kafka.clients.producer.Producer
import java.time.Duration
import java.util.concurrent.TimeUnit

class PeriodicKafkaQueueProcessor(
    private val repository: RecordQueueRepository,
    private val recordProducer: Producer<String, String>,
    private val leaderElection: PodLeaderElection,
    private val batchSize: Int = 1000,
    private val syncTimeoutSeconds: Long = 15,
    internal: Duration = Duration.ofSeconds(2)
): PeriodicJob(internal) {
    private val log = KotlinLogging.logger { }
    private val teamLog = TeamLogs.logger { }

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
            processQueue()
        }
    }

    private fun processQueue() {

        reportQueueSize()

        val nextInQueue = repository.peekNext(batchSize)
        if (nextInQueue.isEmpty()) {
            return
        }

        log.info { "Behandler neste ${nextInQueue.size} elementer i record-queue" }

        val results = nextInQueue.mapNotNull {
            try {
                it to recordProducer.send(it.toKafkaRecord())
            } catch (e: Exception) {
                reportError(Send)
                log.error { "Fikk feil ved sending av kafka-event fra record-queue" }
                teamLog.error(e) { "Fikk feil ved sending kafka-event fra record-queue" }

                null
            }
        }

        try {
            recordProducer.flush()
        } catch (e: Exception) {
            reportError(Flush)
            log.error { "Fikk feil ved flushing av kafka-eventer fra record-queue. Fortsetter prosessering." }
            teamLog.error(e) { "Fikk feil ved flushing av kafka-eventer fra record-queue. Fortsetter prosessering." }
        }

        results
            .forEach { (dto, result) ->
                try {
                    val offsetMetadata = result.get(syncTimeoutSeconds, TimeUnit.SECONDS)
                    if (offsetMetadata.hasOffset()) {
                        repository.dequeueRecord(dto.id)
                        reportEntryProcessed(dto.topic)
                        log.info { "Event er hentet fra record-queue og lagt på kafka" }
                    } else {
                        reportError(Result)
                        log.warn { "Event ble ikke godtatt av kafka av ukjent årsak. Prøver på nytt senere" }
                    }
                } catch (e: Exception) {
                    reportError(Sync)
                    log.error { "Fikk feil ved sending av event til kafka fra record-queue." }
                    teamLog.error(e) { "Fikk feil ved sending av event til kafka fra record-queue." }
                }
            }
    }

    fun isHealthy() = if (job.isActive) {
        AppHealth.Healthy
    } else {
        AppHealth.Unhealthy
    }

    fun flushAndClose() {
        try {
            recordProducer.flush()
            recordProducer.close()
            log.info { "Produsent for kafka-eventer er flushet og lukket." }
        } catch (e: Exception) {
            log.warn { "Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert." }
        }
    }


    private fun reportQueueSize() {
        RECORD_QUEUE_TOTAL_SIZE.set(repository.queueSize().toDouble())
    }

    private fun reportEntryProcessed(topic: String) {
        RECORD_QUEUE_PROCESSED.labelValues(topic).inc()
    }

    private fun reportError(step: RecordSendStep) {
        RECORD_QUEUE_ERROR.labelValues(step.name).inc()
    }

    companion object {
        private enum class RecordSendStep {
            Send, Flush, Sync, Result
        }

        private const val RECORD_QUEUE_TOTAL_SIZE_NAME = "${VarselMetricsReporter.NAMESPACE}_outgoing_record_queue_total_size"
        private const val RECORD_QUEUE_PROCESSED_NAME = "${VarselMetricsReporter.NAMESPACE}_outgoing_record_queue_processed"
        private const val RECORD_QUEUE_ERROR_NAME = "${VarselMetricsReporter.NAMESPACE}_outgoing_record_queue_error"

        private val RECORD_QUEUE_PROCESSED: Counter = Counter.builder()
            .name(RECORD_QUEUE_PROCESSED_NAME)
            .help("Antall utgående kafka-records prosessert fra kø")
            .labelNames("topic")
            .register()

        private val RECORD_QUEUE_TOTAL_SIZE: Gauge = Gauge.builder()
            .name(RECORD_QUEUE_TOTAL_SIZE_NAME)
            .help("Totalt antall utgående kafka-records i kø")
            .register()

        private val RECORD_QUEUE_ERROR: Counter = Counter.builder()
            .name(RECORD_QUEUE_ERROR_NAME)
            .help("Feil i prosessering av eventer")
            .labelNames("steg")
            .register()
    }

}

