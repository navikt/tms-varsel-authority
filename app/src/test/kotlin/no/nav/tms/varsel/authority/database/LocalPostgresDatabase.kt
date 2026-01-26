package no.nav.tms.varsel.authority.database

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import no.nav.tms.common.postgres.Postgres
import no.nav.tms.common.postgres.PostgresDatabase
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

object LocalPostgresDatabase {

    private val container = PostgreSQLContainer("postgres:14.5").apply { start() }
    private val instance: PostgresDatabase by lazy {
        Postgres.connectToContainer(container).also {
            migrate(it.dataSource, expectedMigrations = 4)
        }
    }

    init {
        container.start()
    }

    fun cleanDb(): PostgresDatabase {
        instance.update { queryOf("delete from varsel") }
        instance.update { queryOf("delete from varsel_arkiv") }
        return instance
    }

    private fun migrate(dataSource: HikariDataSource, expectedMigrations: Int) {
        Flyway.configure()
            .connectRetries(3)
            .dataSource(dataSource)
            .load()
            .migrate()
            .let { assert(it.migrationsExecuted == expectedMigrations) }
    }
}
