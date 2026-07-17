package no.nav.tms.varsel.authority.write.outgoing

import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.common.logging.TeamLogs
import no.nav.tms.common.util.scheduling.PeriodicJob
import no.nav.tms.kafka.application.AppHealth
import no.nav.tms.kafka.producer.ProducerSendUtils.batched
import no.nav.tms.kafka.producer.RetriableSendException
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import org.apache.kafka.clients.producer.Producer
import java.time.Duration

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

        try {
            recordProducer.batched(syncTimeoutSeconds) {
                nextInQueue.forEach { dto ->
                    sendInBatch(dto.toKafkaRecord()) {
                        repository.dequeueRecord(dto.id)
                        reportEntryProcessed(dto.topic)
                    }
                }
            }
        } catch (e: RetriableSendException) {
            log.warn { "Midlertidig feil ved sending av eventer fra outbox til kafka. Prøver på nytt senere" }
            teamLog.warn(e) { "Midlertidig feil ved sending av eventer fra outbox til kafka. Prøver på nytt senere" }
        } catch (e: Exception) {
            log.error { "Feil ved sending av eventer fra outbox til kafka. Avslutter prosessering." }
            teamLog.error(e) { "Feil ved sending av eventer fra outbox til kafka. Avslutter prosessering." }
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
            log.warn { "Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble sendt." }
        }
    }

    private fun reportQueueSize() {
        RECORD_QUEUE_TOTAL_SIZE.set(repository.queueSize().toDouble())
    }

    private fun reportEntryProcessed(topic: String) {
        RECORD_QUEUE_PROCESSED.labelValues(topic).inc()
    }

    companion object {

        private const val RECORD_QUEUE_TOTAL_SIZE_NAME = "${VarselMetricsReporter.NAMESPACE}_outgoing_record_queue_total_size"
        private const val RECORD_QUEUE_PROCESSED_NAME = "${VarselMetricsReporter.NAMESPACE}_outgoing_record_queue_processed"

        private val RECORD_QUEUE_PROCESSED: Counter = Counter.builder()
            .name(RECORD_QUEUE_PROCESSED_NAME)
            .help("Antall utgående kafka-records prosessert fra kø")
            .labelNames("topic")
            .register()

        private val RECORD_QUEUE_TOTAL_SIZE: Gauge = Gauge.builder()
            .name(RECORD_QUEUE_TOTAL_SIZE_NAME)
            .help("Totalt antall utgående kafka-records i kø")
            .register()
    }

}

