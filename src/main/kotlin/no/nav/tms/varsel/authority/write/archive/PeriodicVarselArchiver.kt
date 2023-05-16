package no.nav.tms.varsel.authority.write.archive

import mu.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.PeriodicJob
import no.nav.tms.varsel.authority.config.LeaderElection
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import java.time.Duration

class PeriodicVarselArchiver(
    private val varselArchivingRepository: VarselArchiveRepository,
    private val varselArkivertProducer: VarselArkivertProducer,
    private val ageThresholdDays: Int,
    private val leaderElection: LeaderElection,
    private val metricsReporter: VarselMetricsReporter,
    interval: Duration = Duration.ofSeconds(10)
): PeriodicJob(interval) {

    private val log = KotlinLogging.logger {}

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
            archiveOldVarsler()
        }
    }

    private fun archiveOldVarsler() {
        val thresholdDate = nowAtUtc().minusDays(ageThresholdDays.toLong())

        try {
            varselArchivingRepository.archiveOldVarsler(thresholdDate)
                .forEach { arkivertVarsel ->
                    varselArkivertProducer.varselArkivert(arkivertVarsel)
                    metricsReporter.registerVarselArkivert(arkivertVarsel.varsel.type, arkivertVarsel.produsent)
                }

        } catch (e: Exception) {
            log.error("Fikk feil mot databasen ved arkivering av beskjed. Forsøker igjen senere.", e)
        }
    }
}
