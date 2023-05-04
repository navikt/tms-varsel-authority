package no.nav.tms.varsel.authority.done

import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.done.VarselInaktivertKilde.Bruker
import no.nav.tms.varsel.authority.sink.VarselRepository

class VarselBrukerService(
    private val varselRepository: VarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) {
    fun inaktiverVarsel(varselId: String, ident: String) {
        val varsel = varselRepository.getVarsel(varselId)

        if (varsel == null || varsel.ident != ident) {
            throw UnprivilegedAccessException("Varsel forsøkt inaktivert tilhører ikke bruker.")
        } else {
            varselRepository.inaktiverVarsel(varsel.varselId, Bruker)

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
