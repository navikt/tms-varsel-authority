package no.nav.tms.varsel.authority.common

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.token.support.entraid.token.verification.EntraIdUserPrincipal
import no.nav.tms.varsel.authority.read.AdminVarslerRequest
import java.time.Instant

object AdminAuditLog {
    private val log = KotlinLogging.logger("auditLog")

    fun loggHentingAvVarsler(
        entraUser: EntraIdUserPrincipal,
        request: AdminVarslerRequest
    ) {
        log.info {
            AccessLogEntry(
                ansattNavIdent = entraUser.navIdent,
                lookupIdent = request.ident,
                message = request.auditLogMessage(),
                jiraRef = request.jiraRef.valueOrNAN()
            ).toString()
        }
    }

    private class AccessLogEntry(
        ansattNavIdent: String,
        lookupIdent: String,
        message: String,
        jiraRef: String
    ) {
        private val action ="audit:access"
        private val time = Instant.now().toEpochMilli()
        private val content =
            "end=$time suid=$ansattNavIdent duid=$lookupIdent msg=${message.sanitizeForCefFormat()} flexString1Label=Jirasak flexString1=${jiraRef.sanitizeForCefFormat()}"

        override fun toString(): String =
            //Format: CEF:Version|Device Vendor|Device Product|Device Version|Device Event Class ID|Name|Severity|[Extension]
            "CEF:0|Manuell behandling av varsler|tms-varsel-authority|1.0|$action|tms-varsel-authority audit log|INFO|${content}"

        private fun String.sanitizeForCefFormat() =
            this.replace("\\", "\\\\")
                .replace("=", "\\=")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("|", "\\|")
    }
}

private fun String?.valueOrNAN(): String = if (isNullOrEmpty()) {
    "NAN"
} else {
    this
}


