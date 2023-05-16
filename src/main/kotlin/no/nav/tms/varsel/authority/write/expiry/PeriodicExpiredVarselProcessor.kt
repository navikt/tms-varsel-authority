package no.nav.tms.varsel.authority.write.expiry

import no.nav.tms.varsel.authority.common.PeriodicJob
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertHendelse
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Frist
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.config.LeaderElection
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class PeriodicExpiredVarselProcessor(
    private val expiredVarselRepository: ExpiredVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer,
    private val leaderElection: LeaderElection,
    private val metricsReporter: VarselMetricsReporter,
    interval: Duration = Duration.ofMinutes(10)
) : PeriodicJob(interval) {

    private val log: Logger = LoggerFactory.getLogger(PeriodicExpiredVarselProcessor::class.java)

    override val job = initializeJob {
        if (leaderElection.isLeader()) {
           updateExpiredVarsel()
        }
    }

    fun updateExpiredVarsel() {
        try {
            val expiredVarselList = expiredVarselRepository.getExpiredVarsel()

            if (expiredVarselList.isNotEmpty()) {
                expiredVarselRepository.setExpiredVarselInaktiv(expiredVarselList)
                varselInaktivert(expiredVarselList)
                log.info("Prosesserte ${expiredVarselList.size} utgåtte oppgaver.")
            } else {
                log.info("Ingen oppgaver har utgått siden forrige sjekk.")
            }
        } catch (e: Exception) {
            log.error("Uventet feil ved prosessering av utgåtte oppgaver", e)
        }
    }

    private fun varselInaktivert(expiredList: List<ExpiredVarsel>) {
        expiredList.forEach { expired ->
            varselInaktivertProducer.varselInaktivert(
                VarselInaktivertHendelse(
                    varselId = expired.varselId,
                    varselType = expired.varselType,
                    namespace = expired.namespace,
                    appnavn = expired.appnavn,
                    kilde = Frist
                )
            )
            metricsReporter.registerVarselInaktivert(expired.varselType, expired.produsent, Frist)
        }

    }
}
