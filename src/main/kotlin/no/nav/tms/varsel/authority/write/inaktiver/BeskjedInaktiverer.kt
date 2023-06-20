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
    private val varselInaktivertProducer: VarselInaktivertProducer
) {
    suspend fun inaktiverBeskjed(varselId: String, ident: String) = withContext(Dispatchers.IO) {
        val varsel = varselRepository.getVarsel(varselId)

        if (varsel == null) {
            throw VarselNotFoundException("Fant ikke varsel med id $varselId")
        } else if (varsel.ident != ident) {
            throw UnprivilegedAccessException("Kan ikke inaktivere beskjed med id $varselId. Tilhører annen bruker.")
        } else if (varsel.type != Beskjed) {
            throw InvalidVarselTypeException("Bruker kan ikke inaktivere varsel med type ${varsel.type}")
        } else {
            varselRepository.inaktiverVarsel(varsel.varselId, Bruker)

            VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, Bruker)

            varselInaktivertProducer.varselInaktivert(
                VarselInaktivertHendelse(
                    varselId = varsel.varselId,
                    varselType = varsel.type,
                    namespace = varsel.produsent.namespace,
                    appnavn = varsel.produsent.appnavn,
                    kilde = Bruker
                )
            )
        }
    }
}

class UnprivilegedAccessException(message: String): IllegalArgumentException(message)

class InvalidVarselTypeException(message: String): IllegalArgumentException(message)

class VarselNotFoundException(message: String): IllegalArgumentException(message)
