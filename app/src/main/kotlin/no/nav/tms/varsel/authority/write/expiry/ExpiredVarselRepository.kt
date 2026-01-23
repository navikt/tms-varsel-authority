package no.nav.tms.varsel.authority.write.expiry

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.common.postgres.JsonbHelper.json
import no.nav.tms.common.postgres.PostgresDatabase
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Frist
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.common.parseVarseltype

class ExpiredVarselRepository(private val database: PostgresDatabase) {

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
                        type as varseltype,
                        produsent
                """,
                mapOf(
                    "now" to nowAtUtc(),
                    "frist" to Frist.lowercaseName
                )
            )
                .map(toExpiredVasel())
        }
    }

    private fun toExpiredVasel(): (Row) -> ExpiredVarsel = { row ->
        ExpiredVarsel(
            varselId = row.string("varselId"),
            varseltype = row.string("varseltype").let(::parseVarseltype),
            produsent = row.json("produsent")
        )
    }
}

data class ExpiredVarsel(
    val varselId: String,
    val varseltype: Varseltype,
    val produsent: DatabaseProdusent
)
