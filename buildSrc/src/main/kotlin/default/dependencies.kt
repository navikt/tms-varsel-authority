package default

// Managed by tms-dependency-admin. Overrides and additions should be placed in separate file

interface DependencyGroup {
    val groupId: String? get() = null
    val version: String? get() = null

    fun dependency(name: String, groupId: String? = this.groupId, version: String? = this.version): String {
        requireNotNull(groupId)
        requireNotNull(version)

        return "$groupId:$name:$version"
    }
}

interface Auth0Defaults: DependencyGroup {
    override val groupId get() = "com.auth0"
    override val version get() = "3.11.0"

    val javajwt get() = dependency("java-jwt")
}

interface AvroDefaults: DependencyGroup {
    override val groupId get() = "io.confluent"
    override val version get() = "6.2.1"

    val avroSerializer get() = dependency("kafka-avro-serializer")
    val schemaRegistry get() = dependency("kafka-schema-registry")
}

interface AwaitilityDefaults: DependencyGroup {
    override val groupId get() = "org.awaitility"
    override val version get() = "4.0.3"

    val awaitilityKotlin get() = dependency("awaitility-kotlin")
}

interface BouncycastleDefaults: DependencyGroup {
    override val groupId get() = "org.bouncycastle"
    override val version get() = "1.69"

    val bcprovJdk15on get() = dependency("bcprov-jdk15on")
}

interface BrukernotifikasjonDefaults: DependencyGroup {
    override val groupId get() = "com.github.navikt"
    override val version get() = "1.2022.06.10-12.30-e99cac7ce3e2"

    val schemas get() = dependency("brukernotifikasjon-schemas")
}

interface DoknotifikasjonDefaults: DependencyGroup {
    override val groupId get() = "com.github.navikt"
    override val version get() = "1.2020.11.16-09.27-d037b30bb0ea"

    val schemas get() = dependency("doknotifikasjon-schemas")
}

interface DittNAVCommonLibDefaults: DependencyGroup {
    override val version get() = "2022.09.30-12.41-aa46d2d75788"
    override val groupId get() = "com.github.navikt.dittnav-common-lib"

    val influxdb get() = dependency("dittnav-common-influxdb")
    val utils get() = dependency("dittnav-common-utils")
}

interface FlywayDefaults: DependencyGroup {
    override val groupId get() = "org.flywaydb"
    override val version get() = "6.5.7"

    val pluginId get() = "org.flywaydb.flyway"
    val core get() = dependency("flyway-core")
}

interface GraphQLDefaults: DependencyGroup {
    override val groupId get() = "com.expediagroup"
    override val version get() = "5.3.2"

    val pluginId get() = "com.expediagroup.graphql"

    val kotlinClient get() = dependency("graphql-kotlin-client")
    val kotlinKtorClient get() = dependency("graphql-kotlin-ktor-client")
}

interface H2DatabaseDefaults: DependencyGroup {
    override val groupId get() = "com.h2database"
    override val version get() = "2.1.210"

    val h2 get() = dependency("h2")
}

interface HikariDefaults: DependencyGroup {
    override val groupId get() = "com.zaxxer"
    override val version get() = "3.4.5"

    val cp get() = dependency("HikariCP")
}

interface InfluxdbDefaults: DependencyGroup {
    override val groupId get() = "org.influxdb"
    override val version get() = "2.20"

    val java get() = dependency("influxdb-java")
}

interface JacksonDatatypeDefaults: DependencyGroup {
    override val version get() = "2.11.3"

    val datatypeJsr310 get() = dependency("jackson-datatype-jsr310", groupId = "com.fasterxml.jackson.datatype")
    val moduleKotlin get() = dependency("jackson-module-kotlin", groupId = "com.fasterxml.jackson.module")
}

interface JunitDefaults: DependencyGroup {
    override val groupId get() = "org.junit.jupiter"
    override val version get() = "5.4.1"

    val api get() = dependency("junit-jupiter-api")
    val engine get() = dependency("junit-jupiter-engine")
    val params get() = dependency("junit-jupiter-params")
}

interface JjwtDefaults: DependencyGroup {
    override val groupId get() = "io.jsonwebtoken"
    override val version get() = "0.11.5"

    val api get() = dependency("jjwt-api")
    val impl get() = dependency("jjwt-impl")
    val jackson get() = dependency("jjwt-jackson")
    val orgjson get() = dependency("jjwt-orgjson")
}

interface KafkaDefaults: DependencyGroup {
    override val groupId get() = "org.apache.kafka"
    override val version get() = "3.4.0"

    val clients get() = dependency("kafka-clients")
    val kafka_2_12 get() = dependency("kafka_2.12")
    val streams get() = dependency("kafka-streams")
}

interface KluentDefaults: DependencyGroup {
    override val groupId get() = "org.amshove.kluent"
    override val version get() = "1.68"

    val kluent get() = dependency("kluent")
}

interface KotestDefaults: DependencyGroup {
    override val groupId get() = "io.kotest"
    override val version get() = "4.3.1"

    val runnerJunit5 get() = dependency("kotest-runner-junit5")
    val assertionsCore get() = dependency("kotest-assertions-core")
    val extensions get() = dependency("kotest-extensions")
}

interface KotlinDefaults: DependencyGroup {
    override val groupId get() = "org.jetbrains.kotlin"
    override val version get() = "1.8.21"

    val reflect get() = dependency("kotlin-reflect")
}

interface KotlinLoggingDefaults: DependencyGroup {
    override val groupId get() = "io.github.microutils"
    override val version get() = "3.0.0"

    val logging get() = dependency("kotlin-logging")
}

interface KotlinxDefaults: DependencyGroup {
    override val groupId get() = "org.jetbrains.kotlinx"

    val coroutines get() = dependency("kotlinx-coroutines-core", version = "1.3.9")
    val htmlJvm get() = dependency("kotlinx-html-jvm", version = "0.7.3")
    val atomicfu get() = dependency("atomicfu", version = "0.14.4")
    val datetime get() = dependency("kotlinx-datetime", version = "0.3.2")
}

interface KotliQueryDefaults: DependencyGroup {
    override val groupId get() = "com.github.seratch"
    override val version get() = "1.9.0"

    val kotliquery get() = dependency("kotliquery")
}

interface KtorDefaults: DependencyGroup {
    override val version get() = "1.6.7"
    override val groupId get() = "io.ktor"

    val auth get() = dependency("ktor-auth")
    val authJwt get() = dependency("ktor-auth-jwt")
    val htmlBuilder get() = dependency("ktor-html-builder")
    val jackson get() = dependency("ktor-jackson")
    val serverNetty get() = dependency("ktor-server-netty")
    val clientApache get() = dependency("ktor-client-apache")
    val clientJson get() = dependency("ktor-client-json")
    val clientSerializationJvm get() = dependency("ktor-client-serialization-jvm")
    val clientJackson get() = dependency("ktor-client-jackson")
    val clientLogging get() = dependency("ktor-client-logging")
    val clientLoggingJvm get() = dependency("ktor-client-logging-jvm")
    val clientMock get() = dependency("ktor-client-mock")
    val clientMockJvm get() = dependency("ktor-client-mock-jvm")
    val metricsMicrometer get() = dependency("ktor-metrics-micrometer")
    val serverTestHost get() = dependency("ktor-server-test-host")
    val serialization get() = dependency("ktor-serialization")
}

object Ktor2Defaults {
    val version get() = "2.3.0"
    val groupId get() = "io.ktor"

    interface ServerDefaults: DependencyGroup {
        override val groupId get() = Ktor2Defaults.groupId
        override val version get() = Ktor2Defaults.version

        val core get() = dependency("ktor-server-core")
        val netty get() = dependency("ktor-server-netty")
        val defaultHeaders get() = dependency("ktor-server-default-headers")
        val metricsMicrometer get() = dependency("ktor-server-metrics-micrometer")
        val auth get() = dependency("ktor-server-auth")
        val authJwt get() = dependency("ktor-server-auth-jwt")
        val contentNegotiation get() = dependency("ktor-server-content-negotiation")
        val statusPages get() = dependency("ktor-server-status-pages")
        val htmlDsl get() = dependency("ktor-server-html-builder")
        val cors get() = dependency("ktor-server-cors")
    }

    interface ClientDefaults: DependencyGroup {
        override val groupId get() = Ktor2Defaults.groupId
        override val version get() = Ktor2Defaults.version

        val core get() = dependency("ktor-client-core")
        val apache get() = dependency("ktor-client-apache")
        val contentNegotiation get() = dependency("ktor-client-content-negotiation")
    }

    interface SerializationDefaults: DependencyGroup {
        override val groupId get() = Ktor2Defaults.groupId
        override val version get() = Ktor2Defaults.version

        val kotlinX get() = dependency("ktor-serialization-kotlinx-json")
        val jackson get() = dependency("ktor-serialization-jackson")
    }

    interface TestDefaults: DependencyGroup {
        override val groupId get() = Ktor2Defaults.groupId
        override val version get() = Ktor2Defaults.version

        val clientMock get() = dependency("ktor-client-mock")
        val serverTestHost get() = dependency("ktor-server-test-host")
    }
}

interface LogbackDefaults: DependencyGroup {
    override val groupId get() = "ch.qos.logback"
    override val version get() = "1.4.1"

    val classic get() = dependency("logback-classic")
}

interface LogstashDefaults: DependencyGroup {
    override val groupId get() = "net.logstash.logback"
    override val version get() = "6.4"

    val logbackEncoder get() = dependency("logstash-logback-encoder")
}

interface MicrometerDefaults: DependencyGroup {
    override val groupId get() = "io.micrometer"
    override val version get() = "1.7.0"

    val registryPrometheus get() = dependency("micrometer-registry-prometheus")
}

interface MockkDefaults: DependencyGroup {
    override val groupId get() = "io.mockk"
    override val version get() = "1.12.2"

    val mockk get() = dependency("mockk")
}

interface NAVDefaults {
    val vaultJdbc get() = "no.nav:vault-jdbc:1.3.7"
    val kafkaEmbedded get() = "no.nav:kafka-embedded-env:2.8.1"
    val tokenValidatorKtor get() = "no.nav.security:token-validation-ktor:1.3.10"
    val tokenValidatorKtor2 get() = "no.nav.security:token-validation-ktor-v2:2.1.4"
}

interface PostgresqlDefaults: DependencyGroup {
    override val groupId get() = "org.postgresql"
    override val version get() = "42.5.1"

    val postgresql get() = dependency("postgresql")
}

interface PrometheusDefaults: DependencyGroup {
    override val version get() = "0.9.0"
    override val groupId get() = "io.prometheus"

    val common get() = dependency("simpleclient_common")
    val hotspot get() = dependency("simpleclient_hotspot")
    val httpServer get() = dependency("simpleclient_httpserver")
    val logback get() = dependency("simpleclient_logback")
    val simpleClient get() = dependency("simpleclient")
}

interface RapidsAndRiversDefaults: DependencyGroup {
    override val groupId get() = "com.github.navikt"
    override val version get() = "2022100711511665136276.49acbaae4ed4"

    val rapidsAndRivers get() = dependency("rapids-and-rivers")
}

interface ShadowDefaults: DependencyGroup {
    override val version get() = "7.1.2"

    val pluginId get() = "com.github.johnrengelman.shadow"
}

interface SulkyUlidDefaults: DependencyGroup {
    override val version get() = "8.2.0"
    override val groupId get() = "de.huxhorn.sulky"

    val sulkyUlid get() = dependency("de.huxhorn.sulky.ulid")
}

interface TestContainersDefaults: DependencyGroup {
    override val version get() = "1.16.2"
    override val groupId get() = "org.testcontainers"

    val junitJupiter get() = dependency("junit-jupiter")
    val testContainers get() = dependency("testcontainers")
    val postgresql get() = dependency("postgresql")
}

interface TmsKtorTokenSupportDefaults: DependencyGroup {
    override val groupId get() = "com.github.navikt.tms-ktor-token-support"
    override val version get() = "2.1.3"

    val authenticationInstaller get() = dependency("token-support-authentication-installer")
    val azureExchange get() = dependency("token-support-azure-exchange")
    val azureValidation get() = dependency("token-support-azure-validation")
    val tokenXValidation get() = dependency("token-support-tokenx-validation")
    val authenticationInstallerMock get() = dependency("token-support-authentication-installer-mock")
    val tokenXValidationMock get() = dependency("token-support-tokenx-validation-mock")
    val azureValidationMock get() = dependency("token-support-azure-validation-mock")
    val tokendingsExchange get() = dependency("token-support-tokendings-exchange")
    val idportenSidecar get() = dependency("token-support-idporten-sidecar")
    val idportenSidecarMock get() = dependency("token-support-idporten-sidecar-mock")
}

interface UnleashDefaults: DependencyGroup {
    override val version get() = "3.3.1"
    override val groupId get() = "no.finn.unleash"

    val clientJava get() = dependency("unleash-client-java")
}

