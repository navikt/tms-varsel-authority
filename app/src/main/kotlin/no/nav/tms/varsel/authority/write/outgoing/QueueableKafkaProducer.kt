package no.nav.tms.varsel.authority.write.outgoing

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.logging.TeamLogs
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class QueueableKafkaProducer(
    private val repository: RecordQueueRepository,
    private val recordProducer: Producer<String, String>
) {
    private val log = KotlinLogging.logger { }
    private val teamLog = TeamLogs.logger { }

    fun send(topic: String, key: String, value: String) {
        try {
            ProducerRecord(topic, key, value)
                .let(recordProducer::send)
                .get() // Force sync
        } catch (e: Exception) {
            log.error { "Fikk feil ved sending av event til kafka" }
            teamLog.error(e) { "Fikk feil ved sending av event til kafka" }
            throw KafkaProducerException(e)
        }
    }

    fun enqueue(topic: String, key: String, value: String) {
        repository.enqueueRecord(topic, key, value)
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

class KafkaProducerException(cause: Exception): RuntimeException(cause)


