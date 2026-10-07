package no.nav.tms.varsel.authority.read

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.tms.token.support.entraid.token.verification.EntraIdUserPrincipal
import no.nav.tms.varsel.authority.common.AdminAuditLog
import no.nav.tms.varsel.authority.config.Source
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktiverer
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Route.adminApi(
    varselUpdater: VarselInaktiverer,
    readRepository: ReadVarselRepository
) {

    route("varsel/admin") {

        post("inaktiver") {
            val request = call.inaktiverVarselRequest()

            varselUpdater.inaktiverVarselForAdmin(request.varselId, request.grunn)

            call.respond(HttpStatusCode.OK)
        }

        post("alle") {
            VarselMetricsReporter.registerVarselHentet(
                source = Source.ADMIN,
                varseltype = null
            )
            val request = call.receive<AlleVarslerRequest>()

            val alleVarler = readRepository.getAlleVarselForUserIncludeArchived(
                ident = request.ident,
                timeRange = request.timeRange()
            )
            AdminAuditLog.loggHentingAvVarsler(call.entraUser, request)

            call.respond(alleVarler)
        }


        post("aktive") {
            val request = call.receive<AktiveVarselRequest>()
            val aktiveVarsler = readRepository.getVarselSammendragForUser(ident = request.ident, aktiv = true)
            AdminAuditLog.loggHentingAvVarsler(call.entraUser, request)
            call.respond(aktiveVarsler)
        }
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

abstract class AdminVarslerRequest(
    val ident: String,
    val aarsak: String,
    val jiraRef: String?
) {
    abstract fun auditLogMessage(): String
}

class AktiveVarselRequest(ident: String, aarsak: String, jiraRef: String?) :
    AdminVarslerRequest(ident, aarsak, jiraRef) {
    override fun auditLogMessage(): String = "Henting av aktive varsler grunnet ${aarsak}"
}

class AlleVarslerRequest(
    val fom: String,
    val tom: String,
    ident: String,
    aarsak: String,
    jiraRef: String?
) : AdminVarslerRequest(ident, aarsak, jiraRef) {
    fun timeRange() = Timerange(fom, tom)
    override fun auditLogMessage(): String =
        "Henting av alle varsler i perioden $fom til $tom grunnet $aarsak"
}

internal val ApplicationCall.entraUser
    get() = principal<EntraIdUserPrincipal>()
        ?: throw IllegalStateException("Fant ikke EntraIdUserPrincipal i context")