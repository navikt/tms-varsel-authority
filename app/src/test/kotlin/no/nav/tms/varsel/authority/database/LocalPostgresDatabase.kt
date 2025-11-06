package no.nav.tms.varsel.authority.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.Database
import no.nav.tms.varsel.authority.common.toJsonb
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

class LocalPostgresDatabase private constructor() : Database {

    private val memDataSource: HikariDataSource
    private val container = PostgreSQLContainer<Nothing>("postgres:14.5")

    companion object {
        private val instance by lazy {
            LocalPostgresDatabase().also {
                it.migrate(expectedMigrations = 4)
            }
        }

        fun cleanDb(): LocalPostgresDatabase {
            instance.update { queryOf("delete from varsel") }
            instance.update { queryOf("delete from varsel_arkiv") }
            return instance
        }
    }

    init {
        container.start()
        memDataSource = createDataSource()
    }

    override val dataSource: HikariDataSource
        get() = memDataSource

    private fun createDataSource(): HikariDataSource {
        return HikariDataSource().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
            isAutoCommit = true
            validate()
        }
    }

    private fun migrate(expectedMigrations: Int) {
        Flyway.configure()
            .connectRetries(3)
            .dataSource(dataSource)
            .load()
            .migrate()
            .let { assert(it.migrationsExecuted == expectedMigrations) }
    }

    fun insertArkivertVarsel(ident: String, varselId: String, jsonBlob: String) {
        instance.update {
            queryOf(
                """
                    insert into varsel_arkiv(varselId, ident, varsel, arkivert)
                    values (:varselId, :ident, :varsel::jsonb, now())
                    """,
                mapOf(
                    "varselId" to varselId,
                    "ident" to ident,
                    "varsel" to jsonBlob
                )
            )
        }
    }
}

