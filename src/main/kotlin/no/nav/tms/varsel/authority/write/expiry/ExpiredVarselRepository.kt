package no.nav.tms.varsel.authority.write.expiry

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Frist
import no.nav.tms.varsel.authority.VarselType

class ExpiredVarselRepository(private val database: Database) {

    fun getExpiredVarsel(): List<ExpiredVarsel> {
        return database.list {
            queryOf("""
                    select 
                         varselId,
                         type as varselType,
                         produsent->>'namespace' as namespace,
                         produsent->>'appnavn' as appnavn
                    from varsel
                    where aktivFremTil < :now
                """,
                mapOf("now" to nowAtUtc())
            )
            .map(toExpiredVasel())
            .asList
        }
    }

    fun setExpiredVarselInaktiv(expiredVarsel: List<ExpiredVarsel>) {
        database.update {
            val varselIds = it.createArrayOf("VARCHAR", expiredVarsel.map { it.varselId })

            queryOf("""
                update varsel set 
                    aktiv = false,
                    inaktivert = :now,
                    inaktivertAv = :frist
                where
                    varselId = any(:varselIds)
                """,
                mapOf(
                    "varselIds" to varselIds,
                    "now" to nowAtUtc(),
                    "frist" to Frist.lowercaseName
                )
            )
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

