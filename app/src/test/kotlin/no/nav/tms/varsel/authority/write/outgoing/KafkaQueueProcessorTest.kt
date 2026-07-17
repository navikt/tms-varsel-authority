package no.nav.tms.varsel.authority.write.outgoing

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.mockProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.errors.TimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Duration

class KafkaQueueProcessorTest {
    private val database = LocalPostgresDatabase.getCleanInstance()
    private val leaderElection: PodLeaderElection = mockk()

    private val mockProducer = mockProducer()
    private val repository = RecordQueueRepository(database)

    val testTopic = "test-topic"

    @AfterEach
    fun cleanUp() {
        clearMocks(leaderElection)
        mockProducer.clear()
        mockProducer.sendException = null
        mockProducer.flushException = null
        LocalPostgresDatabase.resetInstance()
    }

    @Test
    fun `sender henter records fra kø og sender til kafka synkront`() {
        coEvery { leaderElection.isLeader() } returns true

        val kafkaProducer = initProcessor(2)

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
    fun `Hopper over eventer der sending til kafka feilet`() {
        coEvery { leaderElection.isLeader() } returns true

        val kafkaProducer = initProcessor(2, Duration.ofMillis(200))

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


    @Test
    fun `Fortsetter prosessering dersom flush av eventer feilet`() {
        coEvery { leaderElection.isLeader() } returns true

        val kafkaProducer = initProcessor(2, Duration.ofMillis(200))

        repository.enqueueRecord(testTopic, "key-1", "apple")
        repository.enqueueRecord(testTopic, "key-2", "banana")
        repository.enqueueRecord(testTopic, "key-3", "orange")

        mockProducer.flushException = TimeoutException()

        kafkaProducer.start()

        runBlocking {
            delayUntilQueueEmpty()
        }

        mockProducer.history().size shouldBe 3
    }

    @Test
    @Disabled // Denne testen er svært sårbar for race-conditions og lar seg ofte ikke kjøre sammen med andre tester
    fun `Forsøker på nytt senere dersom event ikke er bekreftet lagt på kafka`() {
        coEvery { leaderElection.isLeader() } returns true

        val manualMockProducer = mockProducer(false)

        val kafkaProducer = initProcessor(
            5,
            interval = Duration.ofMillis(200),
            mockedProducer = manualMockProducer
        )

        repository.enqueueRecord(testTopic, "key-1", "apple")
        repository.enqueueRecord(testTopic, "key-2", "banana")
        repository.enqueueRecord(testTopic, "key-3", "orange")

        manualMockProducer.flushException = TimeoutException()

        kafkaProducer.start()

        runBlocking {
            delay(300)
        }

        manualMockProducer.completeNext()
        manualMockProducer.errorNext(RuntimeException())
        manualMockProducer.completeNext()

        runBlocking {
            delay(100)
        }

        repository.peekNext(5).let {
            it.size shouldBe 1
            it.first().recordValue shouldBe "banana"
        }

        runBlocking {
            delay(300)
        }

        manualMockProducer.completeNext()

        runBlocking {
            delayUntilQueueEmpty()
        }

        repository.peekNext(1).shouldBeEmpty()

        manualMockProducer.history()
            .map { it.value() }
            .let { values ->
                values.shouldContain("apple")
                values.shouldContain("banana")
                values.shouldContain("orange")
            }
    }

    @Test
    @Disabled // Denne testen er svært sårbar for race-conditions og lar seg ofte ikke kjøre sammen med andre tester
    fun `Forsøker på nytt senere dersom kafka ikke svarer i tide ved synkronisering`() {
        coEvery { leaderElection.isLeader() } returns true

        val manualMockProducer = mockProducer(false)

        val kafkaProducer = initProcessor(
            5,
            interval = Duration.ofMillis(200),
            mockedProducer = manualMockProducer,
            syncTimeoutSeconds = 1
        )

        repository.enqueueRecord(testTopic, "key-1", "apple")

        manualMockProducer.flushException = TimeoutException()

        kafkaProducer.start()

        runBlocking {
            delay(1200)
        }

        repository.peekNext(5).let {
            it.size shouldBe 1
            it.first().recordValue shouldBe "apple"
        }

        manualMockProducer.flushException = null

        runBlocking {
            delayUntilQueueEmpty()
        }

        repository.peekNext(1).shouldBeEmpty()

        manualMockProducer.history()
            .map { it.value() }
            .first() shouldContain "apple"
    }

    private suspend fun delayUntilQueueEmpty() {
        withTimeout(5000) {
            while (repository.queueSize() > 0) {
                delay(100)
            }
        }
    }

    private fun initProcessor(
        batchSize: Int,
        interval: Duration = Duration.ofSeconds(3),
        mockedProducer: Producer<String, String> = mockProducer,
        syncTimeoutSeconds: Long = 15
    ): PeriodicKafkaQueueProcessor {
        return PeriodicKafkaQueueProcessor(repository, mockedProducer, leaderElection, batchSize, syncTimeoutSeconds, interval)
    }
}
