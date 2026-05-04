package no.nav.tms.varsel.authority.write.inaktiver

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.tms.varsel.action.Varseltype
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
        val varsel = varselRepository.getVarsel(varselId)

        withInaktiverVarselMdc(
            varselId = varselId,
            initiatedBy = "bruker",
            type = varsel?.type
        ) {
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
        val varsel = varselRepository.getVarsel(varselId)

        withInaktiverVarselMdc(
            varselId = varselId,
            initiatedBy = "admin",
            type = varsel?.type
        ) {
            when (varsel) {
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

    private fun withInaktiverVarselMdc(
        varselId: String,
        initiatedBy: String,
        type: Varseltype? = null,
        function: () -> Unit
    ) {
        val mdcMap = mutableMapOf(
            "minside_id" to varselId,
            "event" to "inaktiver",
            "initiated_by" to initiatedBy
        )

        if (type != null) {
            mdcMap["type"] = type.name.lowercase()
        }

        withLoggingContext(
            map = mapOf(
                "minside_id" to varselId,
                "event" to "inaktiver",
                "initiated_by" to initiatedBy
            ),
            body = function
        )
    }
}

class UnprivilegedAccessException(message: String) : IllegalArgumentException(message)

class InvalidVarseltypeException(message: String, val type: String) : IllegalArgumentException(message)

class VarselNotFoundException(message: String) : IllegalArgumentException(message)
