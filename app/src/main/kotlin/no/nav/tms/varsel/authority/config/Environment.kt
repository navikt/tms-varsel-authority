package no.nav.tms.varsel.authority.config

import no.nav.tms.common.util.config.IntEnvVar.getEnvVarAsInt
import no.nav.tms.common.util.config.StringEnvVar.getEnvVar

data class Environment(
    val jdbcUrl: String = jdbcUrl(),
    val archivingThresholdDays: Int = getEnvVarAsInt("ARCHIVING_THRESHOLD"),
    val kafkaBrokers: String = getEnvVar("KAFKA_BROKERS"),
    val kafkaTruststorePath: String = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    val kafkaKeystorePath: String = getEnvVar("KAFKA_KEYSTORE_PATH"),
    val kafkaCredstorePassword: String = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    val kafkaConsumerGroupId: String = getEnvVar("KAFKA_GROUP_ID"),
    val internalVarselTopic: String = "min-side.brukervarsel-v1",
    val publicVarselTopic: String = "min-side.aapen-brukervarsel-v1"
)

private fun jdbcUrl(): String {
    val host: String = getEnvVar("DB_HOST")
    val name: String = getEnvVar("DB_DATABASE")
    val user: String = getEnvVar("DB_USERNAME")
    val password: String = getEnvVar("DB_PASSWORD")

    return "jdbc:postgresql://${host}/$name?user=$user&password=$password"
}
