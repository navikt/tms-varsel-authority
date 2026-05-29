package no.nav.tms.varsel.authority.write

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotliquery.queryOf
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.common.logging.TeamLogs
import no.nav.tms.common.postgres.PostgresDatabase
import no.nav.tms.common.util.scheduling.PeriodicJob
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration

class RetryingKafkaProducer(
    private val repository: RecordQueueRepository,
    private val kafkaProducer: Producer<String, String>,
    private val leaderElection: PodLeaderElection,
    private val batchSize: Int = 1000,
    internal: Duration = Duration.ofSeconds(10)
): PeriodicJob(internal) {
    val callbackScope = CoroutineScope(Dispatchers.IO + Job())

    private val log = KotlinLogging.logger { }
    private val teamLog = TeamLogs.logger { }

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
            processQueue()
        }
    }

    fun send(event: ProducerRecord<String, String>) {
        val result = kafkaProducer.send(event)

        callbackScope.launch {
            try {
                result.get()
            } catch (e: Exception) {
                log.error { "Fikk feil ved sending av event til kafka. Legger i retry-kø." }
                teamLog.error(e) { "Fikk feil ved sending av event til kafka. Legger i retry-kø." }
                repository.enqueue(event)
            }
        }
    }

    fun flushAndClose() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            log.info { "Produsent for kafka-eventer er flushet og lukket." }
        } catch (e: Exception) {
            log.warn { "Klarte ikke å flushe og lukke produsent. Det kan være eventer som ikke ble produsert." }
        }
    }

    private fun processQueue() {
        repository.nextInQueue(batchSize).forEach { dto ->
            try {
                kafkaProducer.send(dto.toKafkaRecord()).get()
                repository.dequeue(dto.id)
            } catch (e: Exception) {

                log.error { "Fikk feil ved sending av event til kafka fra retry-kø." }
                teamLog.error(e) { "Fikk feil ved sending av event til kafka fra retry-kø." }
                return
            }
        }
    }
}

class RecordQueueRepository(private val database: PostgresDatabase) {
    fun enqueue(record: ProducerRecord<String, String>) {
        database.update {
            queryOf("""
                insert into record_retry_queue(topic, recordKey, recordValue, createdAt)
                values(:topic, :recordKey, :recordValue, :createdAt)
            """, mapOf(
                "topic" to record.topic(),
                "recordKey" to record.key(),
                "recordValue" to record.value(),
                "createdAt" to ZonedDateTimeHelper.nowAtUtc(),
            ))
        }
    }

    fun dequeue(id: Long) {
        database.update {
            queryOf("""
                delete from record_retry_queue where id = :entryId
            """, mapOf(
                "entryId" to id
            ))
        }
    }

    fun nextInQueue(batchSize: Int): List<RecordQueueDto> {
        return database.list {
            queryOf("""
                select
                    id,
                    topic,
                    recordKey,
                    recordValue
                from 
                    record_retry_queue
                order by createdAt
                limit :batchSize
            """, mapOf("batchSize" to batchSize)
                ).map { row ->
                RecordQueueDto(
                    id = row.long("id"),
                    topic = row.string("topic"),
                    recordKey = row.string("recordKey"),
                    recordValue = row.string("recordValue")
                )
            }
        }
    }


    data class RecordQueueDto(
        val id: Long,
        val topic: String,
        val recordKey: String,
        val recordValue: String
    ) {
        fun toKafkaRecord() = ProducerRecord(topic, recordKey, recordValue)
    }
}
