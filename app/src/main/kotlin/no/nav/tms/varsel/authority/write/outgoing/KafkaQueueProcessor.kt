package no.nav.tms.varsel.authority.write.outgoing

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.common.logging.TeamLogs
import no.nav.tms.common.util.scheduling.PeriodicJob
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class KafkaQueueProcessor(
    private val repository: RecordQueueRepository,
    private val recordProducer: Producer<String, String>,
    private val leaderElection: PodLeaderElection,
    private val batchSize: Int = 1000,
    internal: Duration = Duration.ofSeconds(5)
): PeriodicJob(internal) {
    private val log = KotlinLogging.logger { }
    private val teamLog = TeamLogs.logger { }

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
            processQueue()
        }
    }

    private fun processQueue() {

        val nextInQueue = repository.nextInQueue(batchSize)

        if (nextInQueue.isEmpty()) {
            return
        }

        log.info { "Behandler neste ${nextInQueue.size} elementer i record-queue" }

        nextInQueue.forEach { dto ->
            try {
                recordProducer.send(dto.toKafkaRecord()).get()
                repository.dequeueRecord(dto.id)
                log.info { "Event er hentet fra record-queue og lagt på kafka" }
            } catch (e: Exception) {

                log.error { "Fikk feil ved sending av event til kafka fra record-queue." }
                teamLog.error(e) { "Fikk feil ved sending av event til kafka fra record-queue." }
                return
            }
        }
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
}

