package no.nav.tms.varsel.authority.config

import no.nav.personbruker.dittnav.common.util.config.BooleanEnvVar.getEnvVarAsBoolean
import no.nav.personbruker.dittnav.common.util.config.IntEnvVar.getEnvVarAsInt
import no.nav.personbruker.dittnav.common.util.config.StringEnvVar.getEnvVar

data class Environment(
    val groupId: String = getEnvVar("GROUP_ID"),
    val clusterName: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val namespace: String = getEnvVar("NAIS_NAMESPACE"),
    val aivenBrokers: String = getEnvVar("KAFKA_BROKERS"),
    val aivenSchemaRegistry: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val securityConfig: SecurityConfig = SecurityConfig(),
    val dbUser: String = getEnvVar("DB_USERNAME"),
    val dbPassword: String = getEnvVar("DB_PASSWORD"),
    val dbHost: String = getEnvVar("DB_HOST"),
    val dbPort: String = getEnvVar("DB_PORT"),
    val dbName: String = getEnvVar("DB_DATABASE"),
    val dbUrl: String = getDbUrl(dbHost, dbPort, dbName),
    val doknotifikasjonStatusGroupId: String = getEnvVar("GROUP_ID_DOKNOTIFIKASJON_STATUS"),
    val doknotifikasjonStatusTopicName: String = getEnvVar("DOKNOTIFIKASJON_STATUS_TOPIC"),
    val rapidTopic: String = getEnvVar("RAPID_TOPIC"),
    val rapidWriteToDb: Boolean = getEnvVar("RAPID_WRITE_TO_DB", "false").toBoolean(),
    val archivingEnabled: Boolean = getEnvVarAsBoolean("ARCHIVING_ENABLED"),
    val archivingThresholdDays: Int = getEnvVarAsInt("ARCHIVING_THRESHOLD")
) {
    fun rapidConfig() = mapOf(
        "KAFKA_BROKERS" to aivenBrokers,
        "KAFKA_CONSUMER_GROUP_ID" to "tms-varsel-authority-v1",
        "KAFKA_RAPID_TOPIC" to rapidTopic,
        "KAFKA_KEYSTORE_PATH" to securityConfig.variables.aivenKeystorePath,
        "KAFKA_CREDSTORE_PASSWORD" to securityConfig.variables.aivenCredstorePassword,
        "KAFKA_TRUSTSTORE_PATH" to securityConfig.variables.aivenTruststorePath,
        "KAFKA_RESET_POLICY" to "earliest",
        "HTTP_PORT" to "8080"
    )
}

data class SecurityConfig(val variables: SecurityVars = SecurityVars())

data class SecurityVars(
    val aivenTruststorePath: String = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    val aivenKeystorePath: String = getEnvVar("KAFKA_KEYSTORE_PATH"),
    val aivenCredstorePassword: String = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    val aivenSchemaRegistryUser: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val aivenSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD")
)

fun getDbUrl(host: String, port: String, name: String): String {
    return if (host.endsWith(":$port")) {
        "jdbc:postgresql://${host}/$name"
    } else {
        "jdbc:postgresql://${host}:${port}/${name}"
    }
}
