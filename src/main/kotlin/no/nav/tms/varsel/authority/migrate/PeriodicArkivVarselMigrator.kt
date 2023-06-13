package no.nav.tms.varsel.authority.migrate

import mu.KotlinLogging
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.VarselType.*
import no.nav.tms.varsel.authority.common.PeriodicJob
import no.nav.tms.varsel.authority.config.LeaderElection
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

class PeriodicArkivVarselMigrator(
    private val migrationRepository: MigrationRepository,
    private val siphonConsumer: SiphonConsumer,
    private val leaderElection: LeaderElection,
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
                migrateNextBatch()
            } catch (e: Exception) {
                log.warn("Feil ved migrering av arkiverte varsler. Forsøker igjen.", e)
            }
        }
    }

    private suspend fun migrateNextBatch() {
        val type = varselTyper.first()

        val isComplete = migrateBatchOfType(type)

        if (isComplete) {
            finalizeMigrationOfType(type)
        }
    }

    private suspend fun finalizeMigrationOfType(type: VarselType) {
        varselTyper.remove(type)
        if (varselTyper.isEmpty()) {
            log.info("Migrering av arkiverte varsler fullført.")
            stop()
        }
    }

    private suspend fun migrateBatchOfType(type: VarselType): Boolean {
        val mostRecentlyMigrated = migrationRepository.getMostRecentArkivertVarsel(type)

        val fromDate = mostRecentlyMigrated?.arkivert ?: lowerTimeThreshold

        val startTime = Instant.now()

        log.info("Migrerer batch med arkivert $type...")

        val varsler = siphonConsumer.fetchArkivVarsler(type, fromDate, upperTimeThreshold, batchSize)

        return if (varsler.isNotEmpty()) {
            log.info("Fant ingen flere $type arkivert før $upperTimeThreshold.")
            true
        } else {
            val count = migrationRepository.migrateArkivVarsler(varsler)
            val duplicates = varsler.size - count
            val time = Duration.between(startTime, Instant.now()).toMillis()
            MigrationMetricsReporter.registerArkivertVarselMigrert(type, count, duplicates)

            log.info("Migrerte $count arkiverte varsler av $type på $time ms. Forsøkt: ${varsler.size}. Duplikat: $duplicates")
            false
        }
    }
}
