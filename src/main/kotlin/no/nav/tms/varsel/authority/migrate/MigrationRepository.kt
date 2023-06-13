package no.nav.tms.varsel.authority.migrate

import kotliquery.Row
import kotliquery.queryOf
import no.nav.tms.varsel.authority.VarselType
import no.nav.tms.varsel.authority.VarselType.Beskjed
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.toJsonb
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import java.time.ZonedDateTime

class MigrationRepository(private val database: Database) {

    private val objectMapper = defaultObjectMapper()

    fun migrateVarsler(varsler: List<LegacyVarsel>): Int {
        return insertVarsler(varsler).also { result ->
            insertVarselMigrationLog(result)
        }.count { it.duplikat.not() }
    }

    fun migrateArkivVarsler(varsler: List<LegacyArkivertVarsel>): Int {
        return insertArkivVarsler(varsler).also { resultLog ->
            insertArkivertVarselMigrationLog(resultLog)
        }.count { it.duplikat.not() }
    }

    private fun insertVarsler(varsler: List<LegacyVarsel>): List<VarselMigrationLogEntry> {
        return database.batch(
            """
                insert into varsel(
                    varselId,
                    type,
                    ident,
                    sensitivitet,
                    innhold,
                    produsent,
                    eksternVarslingBestilling,
                    eksternVarslingStatus,
                    aktiv,
                    opprettet,
                    aktivFremTil,
                    inaktivert,
                    inaktivertAv
                ) values (
                    :varselId,
                    :type,
                    :ident,
                    :sensitivitet,
                    :innhold,
                    :produsent,
                    :eksternVarslingBestilling,
                    :eksternVarslingStatus,
                    :aktiv,
                    :opprettet,
                    :aktivFremTil,
                    :inaktivert,
                    :inaktivertAv
                )
                on conflict do nothing
            """,
            varsler.map { varsel ->
                mapOf(
                    "varselId" to varsel.aktiv,
                    "type" to varsel.type.lowercaseName,
                    "ident" to varsel.fodselsnummer,
                    "sensitivitet" to varsel.sensitivitet.lowercaseName,
                    "innhold" to varsel.mapInnhold().toJsonb(),
                    "produsent" to varsel.mapProdusent().toJsonb(),
                    "eksternVarslingBestilling" to varsel.mapEksternVarslingBestilling().toJsonb(objectMapper),
                    "eksternVarslingStatus" to varsel.eksternVarslingStatus.toJsonb(objectMapper),
                    "aktiv" to varsel.aktiv,
                    "opprettet" to varsel.forstBehandlet,
                    "aktivFremTil" to varsel.synligFremTil,
                    "inaktivert" to (if (varsel.aktiv) null else varsel.sistOppdatert),
                    "inaktivertAv" to when {
                        varsel.aktiv -> null
                        varsel.fristUtlopt -> VarselInaktivertKilde.Frist.lowercaseName
                        varsel.type == Beskjed -> VarselInaktivertKilde.Bruker.lowercaseName
                        else -> VarselInaktivertKilde.Produsent.lowercaseName
                    }
                )
            }
        ).toVarselMigrationLog(varsler)
    }

    private fun insertArkivVarsler(varsler: List<LegacyArkivertVarsel>): List<ArkivertVarselMigrationLogEntry> {
        return database.batch(
            """
                insert into varsel_arkiv(varselId, ident, varsel, arkivert)
                values(:varselId, :ident, :varsel, :arkivert)
                on conflict do nothing
            """,
            varsler.map {
                mapOf(
                    "varselId" to it.eventId,
                    "ident" to it.fodselsnummer,
                    "varsel" to it.toJsonb(objectMapper),
                    "arkivert" to it.arkivert,
                )
            }
        ).toArkivertVarselMigrationLog(varsler)
    }

    fun getMostRecentVarsel(type: VarselType): VarselMigrationLogEntry? {
        return database.singleOrNull {
            queryOf("""
                select 
                  type,
                  varselId,
                  duplikat,
                  forstBehandlet,
                  migrert
                from varsel_migration_log
                where
                  type = :type
                order by forstBehandlet desc
                  limit 1
            """,
                mapOf("type" to type.lowercaseName)
            )
                .map(toVarselLogEntry())
                .asSingle
        }
    }

    fun getMostRecentArkivertVarsel(type: VarselType): ArkivertVarselMigrationLogEntry? {
        return database.singleOrNull {
            queryOf("""
                select 
                  type,
                  varselId,
                  duplikat,
                  arkivert,
                  migrert
                from arkivert_varsel_migration_log
                where 
                  type = :type 
                order by arkivert desc
                  limit 1
            """,
                mapOf("type" to type.lowercaseName)
            )
                .map(toArkivertVarselLogEntry())
                .asSingle
        }
    }

    fun insertVarselMigrationLog(varsler: List<VarselMigrationLogEntry>) {
        database.batch(
            """
                insert into varsel_migration_log(type, varselId, duplikat, forstBehandlet, migrert)
                    values(:type, :varselId, :duplikat, :forstBehandlet, :migrert)
            """,
            varsler.map { varsel ->
                mapOf(
                    "type" to varsel.type.lowercaseName,
                    "varselId" to varsel.varselId,
                    "duplikat" to varsel.duplikat,
                    "forstBehandlet" to varsel.forstBehandlet,
                    "migrert" to nowAtUtc(),
                )
            }
        )
    }

    fun insertArkivertVarselMigrationLog(arkiverteVarsler: List<ArkivertVarselMigrationLogEntry>) {
        database.batch(
            """
                insert into varsel_migration_log(type, varselId, duplikat, arkivert, migrert)
                    values(:type, :varselId, :duplikat, :arkivert, :migrert)
            """,
            arkiverteVarsler.map { varsel ->
                mapOf(
                    "type" to varsel.type.lowercaseName,
                    "varselId" to varsel.varselId,
                    "duplikat" to varsel.duplikat,
                    "arkivert" to varsel.arkivert,
                    "migrert" to nowAtUtc(),
                )
            }
        )
    }

    private fun toVarselLogEntry(): (Row) -> VarselMigrationLogEntry = {
        VarselMigrationLogEntry(
            type = it.string("type").let(VarselType::parse),
            varselId = it.string("varselId"),
            duplikat = it.boolean("duplikat"),
            forstBehandlet = it.zonedDateTime("forstBehandlet")
        )
    }

    private fun toArkivertVarselLogEntry(): (Row) -> ArkivertVarselMigrationLogEntry = {
        ArkivertVarselMigrationLogEntry(
            type = it.string("type").let(VarselType::parse),
            varselId = it.string("varselId"),
            duplikat = it.boolean("duplikat"),
            arkivert = it.zonedDateTime("arkivert")
        )
    }

    private fun List<Int>.toVarselMigrationLog(varsler: List<LegacyVarsel>): List<VarselMigrationLogEntry> {
        return mapIndexed { i, result ->
            val varsel = varsler[i]
            VarselMigrationLogEntry(
                type = varsel.type,
                varselId = varsel.eventId,
                duplikat = result == 0,
                forstBehandlet = varsel.forstBehandlet
            )
        }
    }

    private fun List<Int>.toArkivertVarselMigrationLog(varsler: List<LegacyArkivertVarsel>): List<ArkivertVarselMigrationLogEntry> {
        return mapIndexed { i, result ->
            val varsel = varsler[i]
            ArkivertVarselMigrationLogEntry(
                type = varsel.type,
                varselId = varsel.eventId,
                duplikat = result == 0,
                arkivert = varsel.arkivert
            )
        }
    }
}

data class VarselMigrationLogEntry(
    val type: VarselType,
    val varselId: String,
    val duplikat: Boolean,
    val forstBehandlet: ZonedDateTime
)

data class ArkivertVarselMigrationLogEntry(
    val type: VarselType,
    val varselId: String,
    val duplikat: Boolean,
    val arkivert: ZonedDateTime
)
