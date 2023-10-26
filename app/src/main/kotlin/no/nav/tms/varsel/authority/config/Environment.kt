package no.nav.tms.varsel.authority.config

import no.nav.personbruker.dittnav.common.util.config.IntEnvVar.getEnvVarAsInt
import no.nav.personbruker.dittnav.common.util.config.StringEnvVar.getEnvVar

data class Environment(
    val dbUser: String = getEnvVar("DB_USERNAME"),
    val dbPassword: String = getEnvVar("DB_PASSWORD"),
    val dbUrl: String = getDbUrl(),
    val archivingThresholdDays: Int = getEnvVarAsInt("ARCHIVING_THRESHOLD"),
    val kafkaBrokers: String = getEnvVar("KAFKA_BROKERS"),
    val kafkaTruststorePath: String = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    val kafkaKeystorePath: String = getEnvVar("KAFKA_KEYSTORE_PATH"),
    val kafkaCredstorePassword: String = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    val kafkaConsumerGroupId: String = getEnvVar("KAFKA_GROUP_ID"),
    val internalVarselTopic: String = "min-side.brukervarsel-v1",
    val publicVarselTopic: String = "min-side.aapen-brukervarsel-v1"
) {
    val rapidConfig = mapOf(
        "KAFKA_RAPID_TOPIC" to publicVarselTopic,
        "KAFKA_EXTRA_TOPIC" to internalVarselTopic,
        "KAFKA_BROKERS" to kafkaBrokers,
        "KAFKA_CONSUMER_GROUP_ID" to kafkaConsumerGroupId,
        "KAFKA_KEYSTORE_PATH" to kafkaKeystorePath,
        "KAFKA_CREDSTORE_PASSWORD" to kafkaCredstorePassword,
        "KAFKA_TRUSTSTORE_PATH" to kafkaTruststorePath,
        "KAFKA_RESET_POLICY" to "earliest",
        "HTTP_PORT" to "8080"
    )
}

fun getDbUrl(): String {
    val host: String = getEnvVar("DB_HOST")
    val port: String = getEnvVar("DB_PORT")
    val name: String = getEnvVar("DB_DATABASE")

    return if (host.endsWith(":$port")) {
        "jdbc:postgresql://${host}/$name"
    } else {
        "jdbc:postgresql://${host}:${port}/${name}"
    }
}
