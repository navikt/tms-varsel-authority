package no.nav.tms.varsel.authority.archive

import mu.KotlinLogging
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.PeriodicJob
import no.nav.tms.varsel.authority.common.exceptions.RetriableDatabaseException
import no.nav.tms.varsel.authority.election.LeaderElection
import java.time.Duration

class PeriodicVarselArchiver(
    private val varselArchivingRepository: VarselArchiveRepository,
    private val varselArkivertProducer: VarselArkivertProducer,
    private val ageThresholdDays: Int,
    private val leaderElection: LeaderElection,
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
                .forEach { arkivertVarsel -> varselArkivertProducer.varselArkivert(arkivertVarsel) }

        } catch (rt: RetriableDatabaseException) {
            log.warn("Fikk en periodisk feil mot databasen ved arkivering av Beskjed. Forsøker igjen senere.", rt)
        } catch (e: Exception) {
            log.error("Fikk feil mot databasen ved arkivering av beskjed. Forsøker igjen senere.", e)
        }
    }
}
