package no.nav.tms.varsel.authority.write.inaktiver

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Admin
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Bruker
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository

class VarselInaktiverer(
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) {
    private val log = KotlinLogging.logger {}

    suspend fun inaktiverBeskjedForBruker(varselId: String, ident: String) = withContext(Dispatchers.IO) {
        traceInaktiverVarsel(varselId, Bruker) {
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

                    VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, Bruker)

                    varselInaktivertProducer.enqueueVarselInaktivert(
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
        traceInaktiverVarsel(varselId, Admin) {

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

                    VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, Admin)

                    varselInaktivertProducer.enqueueVarselInaktivert(
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

    private fun traceInaktiverVarsel(varselId: String, kilde: VarselInaktivertKilde, function: () -> Unit) {
        withLoggingContext(
            "minside_id" to varselId,
            "action" to "inaktiver",
            "initiated_by" to kilde.lowercaseName,
        ) {
            function()
        }
    }
}

class UnprivilegedAccessException(message: String) : IllegalArgumentException(message)

class InvalidVarseltypeException(message: String, val type: String) : IllegalArgumentException(message)

class VarselNotFoundException(message: String) : IllegalArgumentException(message)
