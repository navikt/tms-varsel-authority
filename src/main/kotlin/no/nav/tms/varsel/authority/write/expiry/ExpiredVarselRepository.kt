package no.nav.tms.varsel.authority.write.expiry

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.Produsent
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Frist
import no.nav.tms.varsel.authority.VarselType

class ExpiredVarselRepository(private val database: Database) {

    fun updateExpiredVarsel(): List<ExpiredVarsel> {
        return database.list {
            queryOf(
                """
                    update varsel set 
                        aktiv = false,
                        inaktivert = :now,
                        inaktivertAv = :frist
                    where
                        aktiv = true
                        and aktivFremTil < :now
                    returning
                        varselId,
                        type as varselType,
                        produsent->>'namespace' as namespace,
                        produsent->>'appnavn' as appnavn
                """,
                mapOf(
                    "now" to nowAtUtc(),
                    "frist" to Frist.lowercaseName
                )
            )
                .map(toExpiredVasel())
                .asList
        }
    }

    private fun toExpiredVasel(): (Row) -> ExpiredVarsel = { row ->
        ExpiredVarsel(
            varselId = row.string("varselId"),
            varselType = row.string("varselType").let { VarselType.parse(it) },
            namespace = row.string("namespace"),
            appnavn = row.string("appnavn"),
        )
    }
}

data class ExpiredVarsel(
    val varselId: String,
    val varselType: VarselType,
    val namespace: String,
    val appnavn: String
) {
    val produsent get() = Produsent(namespace, appnavn)
}
