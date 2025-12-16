package no.nav.tms.varsel.authority.write.inaktiver

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.tms.varsel.authority.config.Source
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Route.adminApi(
    varselUpdater: VarselInaktiverer,
    readRepository: ReadVarselRepository
) {
    post("/varsel/inaktiver") {
        val request = call.inaktiverVarselRequest()

        varselUpdater.inaktiverVarselForAdmin(request.varselId, request.grunn)

        call.respond(HttpStatusCode.OK)
    }

    post("/varsel/admin/alle") {
        VarselMetricsReporter.registerVarselHentet(
            source = Source.ADMIN,
            varseltype = null
        )
        val request = call.receive<AlleVarslerRequest>()
        call.respond(
            readRepository.getAlleVarselForUserIncludeArchived(
                ident = request.ident,
                timeRange = request.timeRange()
            )
        )
    }
}

private suspend fun ApplicationCall.inaktiverVarselRequest() = try {
    receive<InaktiverVarselRequest>()
} catch (e: ContentTransformationException) {
    throw IllegalArgumentException("Ugyldig format for inaktivering av varsel som admin")
}

data class InaktiverVarselRequest(val varselId: String, val grunn: String)

private val ApplicationRequest.identHeader get() = headers["ident"] ?: throw BadRequestException("Mangler ident-header")

class Timerange(fomQueryParam: String, tomQueryParam: String) {
    val fom = LocalDate.parse(fomQueryParam, formatter).atStartOfDay(ZoneId.of("Europe/Oslo"))
    val tom = LocalDate.parse(tomQueryParam, formatter).atStartOfDay(ZoneId.of("Europe/Oslo"))

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

private fun ApplicationCall.timeRange(): Timerange =
    Timerange(
        fomQueryParam = this.request.queryParameters["fom"] ?: throw BadRequestException("fom parameter må være satt"),
        tomQueryParam = this.request.queryParameters["tom"] ?: throw BadRequestException("tom parameter må være satt")
    )

data class AlleVarslerRequest(val ident: String, val fom: String, val tom: String){
    fun timeRange() = Timerange(fom,tom)
}