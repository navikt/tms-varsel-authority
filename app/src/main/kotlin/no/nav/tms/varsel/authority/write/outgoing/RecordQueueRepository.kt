package no.nav.tms.varsel.authority.write.outgoing

import kotliquery.queryOf
import no.nav.tms.common.postgres.PostgresDatabase
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import org.apache.kafka.clients.producer.ProducerRecord

class RecordQueueRepository(private val database: PostgresDatabase) {
    fun enqueueRecord(topic: String, key: String, value: String) {
        database.update {
            queryOf("""
                insert into outgoing_record_queue(topic, recordKey, recordValue, createdAt)
                values(:topic, :recordKey, :recordValue, :createdAt)
            """, mapOf(
                "topic" to topic,
                "recordKey" to key,
                "recordValue" to value,
                "createdAt" to ZonedDateTimeHelper.nowAtUtc(),
            ))
        }
    }

    fun dequeueRecord(id: Long) {
        database.update {
            queryOf("""
                delete from outgoing_record_queue where id = :entryId
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
                    outgoing_record_queue
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

    fun queueSize(): Int {
        return database.single {
            queryOf("select count(*) as antall from outgoing_record_queue")
                .map { row -> row.int("antall") }
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
