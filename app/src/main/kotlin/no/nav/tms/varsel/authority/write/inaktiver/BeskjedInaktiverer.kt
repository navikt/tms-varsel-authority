package no.nav.tms.varsel.authority.write.inaktiver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Bruker
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository
import no.nav.tms.varsel.action.Varseltype

class BeskjedInaktiverer(
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) {
    private val log = KotlinLogging.logger {}

    private val staticMetadata = emptyMap<String, Any>()

    suspend fun inaktiverBeskjed(varselId: String, ident: String) = withContext(Dispatchers.IO) {
        val varsel = varselRepository.getVarsel(varselId)

        if (varsel == null) {
            throw VarselNotFoundException("Fant ikke varsel med id $varselId")
        } else if (varsel.ident != ident) {
            throw UnprivilegedAccessException("Kan ikke inaktivere beskjed med id $varselId. Tilhører annen bruker.")
        } else if (varsel.type != Varseltype.Beskjed) {
            throw InvalidVarselTypeException("Bruker kan ikke inaktivere varsel med type ${varsel.type}")
        } else {
            log.info { "Inaktiverer beskjed med varselId $varselId på vegne av bruker." }

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
