package no.nav.tms.varsel.authority.write.expiry

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.util.scheduling.PeriodicJob
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertHendelse
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Frist
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import java.time.Duration

class PeriodicExpiredVarselProcessor(
    private val expiredVarselRepository: ExpiredVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer,
    private val leaderElection: no.nav.tms.common.kubernetes.PodLeaderElection,
    interval: Duration = Duration.ofMinutes(1)
) : PeriodicJob(interval) {

    private val log = KotlinLogging.logger { }

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
           updateExpiredVarsel()
        }
    }

    fun updateExpiredVarsel() {
        try {
            val expiredVarselList = expiredVarselRepository.updateExpiredVarsel()

            if (expiredVarselList.isNotEmpty()) {
                varselInaktivert(expiredVarselList)
                log.info { "Prosesserte ${expiredVarselList.size} utgåtte varsler." }
            } else {
                log.info { "Ingen varsler har utgått siden forrige sjekk." }
            }
        } catch (e: Exception) {
            log.error(e) { "Uventet feil ved prosessering av utgåtte varsler" }
        }
    }

    private fun varselInaktivert(expiredList: List<ExpiredVarsel>) {
        expiredList.forEach { expired ->
            varselInaktivertProducer.varselInaktivert(
                VarselInaktivertHendelse(
                    varselId = expired.varselId,
                    varseltype = expired.varseltype,
                    produsent = expired.produsent,
                    kilde = Frist
                )
            )
            VarselMetricsReporter.registerVarselInaktivert(expired.varseltype, expired.produsent, Frist, "N/A")
        }
    }
}
