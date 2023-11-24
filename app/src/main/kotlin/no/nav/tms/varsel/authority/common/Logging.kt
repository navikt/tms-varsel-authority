package no.nav.tms.varsel.authority.common

import observability.traceVarsel

fun traceVarselSink(id: String, initiatedBy: String, action: String, varseltype: String, function: () -> Unit) =
    traceVarsel(id, mapOf("initiated_by" to initiatedBy, "action" to action, "type" to varseltype)) {
        function()
    }