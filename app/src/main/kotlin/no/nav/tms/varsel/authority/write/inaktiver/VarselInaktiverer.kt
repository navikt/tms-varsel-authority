package no.nav.tms.varsel.authority.write.inaktiver

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Admin
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Bruker
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import no.nav.tms.common.observability.traceVarsel
import org.slf4j.MDC

class VarselInaktiverer(
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) {
    private val log = KotlinLogging.logger {}

    suspend fun inaktiverBeskjedForBruker(varselId: String, ident: String) = withContext(Dispatchers.IO) {
        traceVarsel(id = varselId, mapOf("action" to "inaktiver", "initiated_by" to "bruker")) {
            val varsel = varselRepository.getVarsel(varselId)

            when {
                varsel == null -> throw VarselNotFoundException("Fant ikke varsel")
                varsel.ident != ident -> throw UnprivilegedAccessException("Kan ikke inaktivere annen brukers beskjed.")
                varsel.type != Beskjed -> throw InvalidVarseltypeException(
                    "Bruker kan ikke inaktivere varsel med type ${varsel.type}",
                    varsel.type.name
                )

                varsel.aktiv == false -> {
                     log.info { "Ignorer forespørsel om å inaktivere allerede inaktiv beskjed." }
                }

                else -> {
                    log.info { "Inaktiverer beskjed." }

                    varselRepository.inaktiverVarsel(varsel.varselId, Bruker)

                    VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, Bruker, "N/A")

                    varselInaktivertProducer.varselInaktivert(
                        VarselInaktivertHendelse(
                            varselId = varsel.varselId,
                            varseltype = varsel.type,
                            produsent = varsel.produsent,
                            kilde = Bruker
                        )
                    )
                }
            }
        }
    }

    suspend fun inaktiverVarselForAdmin(varselId: String, grunn: String) = withContext(Dispatchers.IO) {
        traceVarsel(varselId, mapOf("action" to "inaktiver", "initiated_by" to "admin")) {

            when (val varsel = varselRepository.getVarsel(varselId)) {
                null -> throw VarselNotFoundException("Fant ikke varsel")
                else -> {
                    log.info { "Inaktiverer varsel." }

                    varselRepository.inaktiverVarsel(
                        varselId = varsel.varselId,
                        kilde = Admin,
                        metadata = mapOf(
                            "admin_action" to mapOf(
                                "inaktiver" to mapOf(
                                    "grunn" to grunn
                                )
                            )
                        )
                    )

                    VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, Admin, "N/A")

                    varselInaktivertProducer.varselInaktivert(
                        VarselInaktivertHendelse(
                            varselId = varsel.varselId,
                            varseltype = varsel.type,
                            produsent = varsel.produsent,
                            kilde = Admin
                        )
                    )
                }
            }
        }
    }
}

class UnprivilegedAccessException(message: String) : IllegalArgumentException(message)

class InvalidVarseltypeException(message: String, val type: String) : IllegalArgumentException(message)

class VarselNotFoundException(message: String) : IllegalArgumentException(message)
