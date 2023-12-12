package no.nav.tms.varsel.authority.write.expiry

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde.Frist
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.common.json
import no.nav.tms.varsel.authority.common.parseVarseltype
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ExpiredVarselRepository(private val database: Database) {

    private val epoch = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))

    private val objectMapper = defaultObjectMapper()

    fun updateAllTimeExpired() = updateExpiredVarsel(epoch)
    fun updateExpiredPastHour() = updateExpiredVarsel(nowAtUtc().minusHours(1))

    private fun updateExpiredVarsel(checkFromDate: ZonedDateTime): List<ExpiredVarsel> {
        return database.list {
            queryOf(
                """
                    update varsel set 
                        aktiv = false,
                        inaktivert = :now,
                        inaktivertAv = :frist
                    where
                        aktiv = true
                        and aktivFremTil between :startDate and :now
                    returning
                        varselId,
                        type as varseltype,
                        produsent
                """,
                mapOf(
                    "startDate" to checkFromDate,
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
