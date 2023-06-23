package no.nav.tms.varsel.authority.config

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration

object Flyway {

    fun runFlywayMigrations(env: Environment) {
        val flyway = configure(env).load()
        flyway.migrate()
    }

    private fun configure(env: Environment): FluentConfiguration {
        val configBuilder = Flyway.configure()
        val dataSource = createDataSourceForLocalDbWithUser(env)
        configBuilder.dataSource(dataSource)

        return configBuilder
    }

    private fun createDataSourceForLocalDbWithUser(env: Environment): HikariDataSource {
        return PostgresDatabase.hikariFromLocalDb(env)
    }

}
