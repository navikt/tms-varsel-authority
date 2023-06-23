package no.nav.tms.varsel.authority.migrate

import mu.KotlinLogging
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.VarselType.*
import no.nav.tms.varsel.authority.common.PeriodicJob
import no.nav.tms.varsel.authority.config.PodLeaderElection
import org.postgresql.util.PSQLException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

class PeriodicVarselMigrator(
    private val migrationRepository: MigrationRepository,
    private val siphonConsumer: SiphonConsumer,
    private val leaderElection: PodLeaderElection,
    upperDateThreshold: String,
    private val batchSize: Int = 1000,
    interval: Duration = Duration.ofSeconds(1)
): PeriodicJob(interval) {

    private val lowerTimeThreshold = ZonedDateTime.parse("2019-01-01T00:00:00Z")
    private val upperTimeThreshold = ZonedDateTime.parse("${upperDateThreshold}T00:00:00Z")

    private val log = KotlinLogging.logger {}

    private val varselTyper = mutableSetOf(
        Beskjed,
        Oppgave,
        Innboks
    )

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
            try {
                val type = varselTyper.first()

                migrateBatchOfType(type)
            } catch (pe: PSQLException) {
                log.warn("Feil ved migrering av varsler. Forsøker igjen.", pe)
            }
        }
    }

    private suspend fun migrateBatchOfType(type: VarselType) {
        val mostRecentlyMigrated = migrationRepository.getMostRecentVarsel(type)

        val fromTime = mostRecentlyMigrated?.forstBehandlet ?: lowerTimeThreshold
        val startTime = Instant.now()

        log.info("Migrerer batch med $type...")

        val varsler = siphonConsumer.fetchVarsler(type, fromTime, upperTimeThreshold, batchSize)

        if (varsler.isEmpty()) {
            finalizeMigrationOfType(type)
            return
        }

        val count = migrationRepository.migrateVarsler(varsler)
        val duplicates = varsler.size - count
        val time = Duration.between(startTime, Instant.now()).toMillis()

        MigrationMetricsReporter.registerVarselMigrert(type, count, duplicates)
        log.info("Migrerte $count varsler av $type på $time ms. Forsøkt: ${varsler.size}. Duplikat: $duplicates")

        if (count == 0) {
            finalizeMigrationOfType(type)
        }
    }

    private suspend fun finalizeMigrationOfType(type: VarselType) {
        log.info("Fant ingen flere $type opprettet før $upperTimeThreshold som ikke er migrert.")
        varselTyper.remove(type)
        if (varselTyper.isEmpty()) {
            log.info("Migrering av varsler fullført.")
            stop()
        }
    }
}
