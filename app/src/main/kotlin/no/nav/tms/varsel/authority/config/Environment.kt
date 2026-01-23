package no.nav.tms.varsel.authority.config

import no.nav.tms.common.util.config.IntEnvVar.getEnvVarAsInt
import no.nav.tms.common.util.config.StringEnvVar.getEnvVar

data class Environment(
    val jdbcUrl: String = getEnvVar("DB_JDBC_URL"),
    val archivingThresholdDays: Int = getEnvVarAsInt("ARCHIVING_THRESHOLD"),
    val kafkaBrokers: String = getEnvVar("KAFKA_BROKERS"),
    val kafkaTruststorePath: String = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    val kafkaKeystorePath: String = getEnvVar("KAFKA_KEYSTORE_PATH"),
    val kafkaCredstorePassword: String = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    val kafkaConsumerGroupId: String = getEnvVar("KAFKA_GROUP_ID"),
    val internalVarselTopic: String = "min-side.brukervarsel-v1",
    val publicVarselTopic: String = "min-side.aapen-brukervarsel-v1"
)
