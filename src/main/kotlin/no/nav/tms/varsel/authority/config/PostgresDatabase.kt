package no.nav.tms.varsel.authority.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.tms.varsel.authority.common.Database

class PostgresDatabase(env: Environment) : Database {

    private val envDataSource: HikariDataSource

    init {
        envDataSource = createConnectionForLocalDbWithDbUser(env)
    }

    override val dataSource: HikariDataSource
        get() = envDataSource

    private fun createConnectionForLocalDbWithDbUser(env: Environment): HikariDataSource {
        return hikariFromLocalDb(env)
    }

    companion object {

        fun hikariFromLocalDb(env: Environment): HikariDataSource {
            val config = hikariCommonConfig(env)
            config.validate()
            return HikariDataSource(config)
        }

        private fun hikariCommonConfig(env: Environment): HikariConfig {
            val config = HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = env.dbUrl
                minimumIdle = 1
                maxLifetime = 1800000
                maximumPoolSize = 5
                connectionTimeout = 4000
                validationTimeout = 1000
                idleTimeout = 30000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                username = env.dbUser
                password = env.dbPassword
            }
            return config
        }
    }
}
