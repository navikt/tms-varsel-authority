package no.nav.tms.varsel.authority.write.inaktiver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.varsel.authority.VarselType.Beskjed
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Bruker
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository

class BeskjedInaktiverer(
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer,
    private val metricsReporter: VarselMetricsReporter
) {
    suspend fun inaktiverBeskjed(varselId: String, ident: String) = withContext(Dispatchers.IO) {
        val varsel = varselRepository.getVarsel(varselId)

        if (varsel == null || varsel.ident != ident) {
            throw UnprivilegedAccessException("Beskjed inaktivert tilhører ikke bruker.")
        } else if (varsel.type != Beskjed) {
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
