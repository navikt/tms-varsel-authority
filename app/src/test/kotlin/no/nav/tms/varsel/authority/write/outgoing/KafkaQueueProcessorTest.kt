package no.nav.tms.varsel.authority.write.outgoing

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.mockProducer
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Duration

class KafkaQueueProcessorTest {
    private val database = LocalPostgresDatabase.cleanDb()
    private val leaderElection: PodLeaderElection = mockk()

    private val mockProducer = mockProducer()
    private val repository = RecordQueueRepository(database)

    val testTopic = "test-topic"

    @AfterEach
    fun cleanUp() {
        clearMocks(leaderElection)
        mockProducer.clear()
        mockProducer.sendException = null
        LocalPostgresDatabase.cleanDb()
    }

    @Test
    fun `sender henter records fra kø og sender til kafka synkront`() {
        coEvery { leaderElection.isLeader() } returns true

        val kafkaProducer = initProducer(2)

        repository.enqueueRecord(testTopic, "key-1", "apple")
        repository.enqueueRecord(testTopic, "key-2", "banana")
        repository.enqueueRecord(testTopic, "key-3", "orange")


        kafkaProducer.start()

        runBlocking {
            delayUntilQueueEmpty()
        }

        mockProducer.history().size shouldBe 3
        mockProducer.history()
            .map { it.value() }
            .let {
                it shouldContain "apple"
                it shouldContain "banana"
                it shouldContain "orange"
            }
    }

    @Test
    fun `Forsøker på nytt senere dersom sending til kafka feiler`() {
        coEvery { leaderElection.isLeader() } returns true

        val kafkaProducer = initProducer(2, Duration.ofMillis(200))

        repository.enqueueRecord(testTopic, "key-1", "apple")
        repository.enqueueRecord(testTopic, "key-2", "banana")
        repository.enqueueRecord(testTopic, "key-3", "orange")

        mockProducer.sendException = TimeoutException()

        kafkaProducer.start()

        runBlocking {
            delay(500)
        }

        mockProducer.history().size shouldBe 0

        mockProducer.sendException = null

        runBlocking {
            delayUntilQueueEmpty()
        }

        mockProducer.history().size shouldBe 3
    }

    private suspend fun delayUntilQueueEmpty() {
        withTimeout(5000) {
            while (repository.queueSize() > 0) {
                delay(100)
            }
        }
    }

    private fun initProducer(batchSize: Int, interval: Duration = Duration.ofSeconds(3)): KafkaQueueProcessor {
        return KafkaQueueProcessor(repository, mockProducer, leaderElection, batchSize, interval)
    }
}
