package no.nav.tms.varsel.authority.write.done

import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.metrics.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.done.VarselInaktivertKilde.Bruker
import no.nav.tms.varsel.authority.write.sink.VarselType
import no.nav.tms.varsel.authority.write.sink.WriteVarselRepository

class VarselBrukerService(
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer,
    private val metricsReporter: VarselMetricsReporter
) {
    fun inaktiverBeskjed(varselId: String, ident: String) {
        val varsel = varselRepository.getVarsel(varselId)

        if (varsel == null || varsel.ident != ident) {
            throw UnprivilegedAccessException("Beskjed inaktivert tilhører ikke bruker.")
        } else if ( varsel.type != VarselType.Beskjed) {
            throw InvalidVarselTypeException("Bruker kan ikke inaktivere varsel med type $")
        } else {
            varselRepository.inaktiverVarsel(varsel.varselId, Bruker)

            metricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, Bruker)
            varselInaktivertProducer.varselInaktivert(
                VarselInaktivertHendelse(
                    varselId = varsel.varselId,
                    varselType = varsel.type,
                    namespace = varsel.produsent.namespace,
                    appnavn = varsel.produsent.appnavn,
                    kilde = Bruker,
                    tidspunkt = nowAtUtc(),
                )
            )
        }
    }

}

class UnprivilegedAccessException(message: String): RuntimeException(message)

class InvalidVarselTypeException(message: String): RuntimeException(message)
