package no.nav.tms.varsel.authority.write.inaktiver

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.inaktiverVarselApi(
    varselUpdater: VarselInaktiverer
) {
    post("/varsel/inaktiver") {
        val request = call.inaktiverVarselRequest()

        varselUpdater.inaktiverVarselForAdmin(request.varselId, request.grunn)

        call.respond(HttpStatusCode.OK)
    }
}

private suspend fun ApplicationCall.inaktiverVarselRequest() = try {
    receive<InaktiverVarselRequest>()
} catch (e: ContentTransformationException) {
    throw IllegalArgumentException("Ugyldig format for inaktivering av varsel som admin")
}

data class InaktiverVarselRequest(val varselId: String, val grunn: String)
