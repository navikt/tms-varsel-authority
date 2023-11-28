package no.nav.tms.varsel.authority.write.inaktiver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Bruker
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.action.Varseltype.Beskjed
import observability.traceVarsel

class BeskjedInaktiverer(
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) {
    private val log = KotlinLogging.logger {}

    suspend fun inaktiverBeskjed(varselId: String, ident: String) = withContext(Dispatchers.IO) {
        traceVarsel(id = varselId, mapOf("action" to "inaktiver", "initiated_by" to "bruker")) {
            val varsel = varselRepository.getVarsel(varselId)

            when {
                varsel == null -> throw VarselNotFoundException("Fant ikke varsel")
                varsel.ident != ident -> throw UnprivilegedAccessException("Kan ikke inaktivere annen brukers beskjed.")
                varsel.type != Beskjed -> throw InvalidVarselTypeException(
                    "Bruker kan ikke inaktivere varsel med type ${varsel.type}",
                    varsel.type.name
                )

                else -> {
                    log.info { "Inaktiverer beskjed." }

                    varselRepository.inaktiverVarsel(varsel.varselId, Bruker)

                    VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, Bruker, "N/A")

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
    }
}

class UnprivilegedAccessException(message: String) : IllegalArgumentException(message)

class InvalidVarselTypeException(message: String, val type: String) : IllegalArgumentException(message)

class VarselNotFoundException(message: String) : IllegalArgumentException(message)
