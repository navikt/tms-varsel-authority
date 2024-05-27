import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm").version(Kotlin.version)

    id(Shadow.pluginId) version (Shadow.version)

    // Apply the application plugin to add support for building a CLI application.
    application
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}


repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    mavenLocal()
}

dependencies {
    implementation(Flyway.core)
    implementation(Hikari.cp)
    implementation(Kafka.clients)
    implementation(Postgresql.postgresql)
    implementation(KotlinLogging.logging)
    implementation(Ktor.Client.core)
    implementation(Ktor.Client.contentNegotiation)
    implementation(Ktor.Client.apache)
    implementation(Ktor.Server.auth)
    implementation(Ktor.Server.contentNegotiation)
    implementation(Ktor.Server.core)
    implementation(Ktor.Server.statusPages)
    implementation(Ktor.Serialization.jackson)
    implementation(Logstash.logbackEncoder)
    implementation(Prometheus.common)
    implementation(TmsKtorTokenSupport.azureValidation)
    implementation(TmsKtorTokenSupport.tokenXValidation)
    implementation(KotliQuery.kotliquery)
    implementation(JacksonDatatype.moduleKotlin)
    implementation(TmsCommonLib.utils)
    implementation(TmsCommonLib.metrics)
    implementation(TmsCommonLib.observability)
    implementation(TmsKafkaTools.kafkaApplication)
    implementation(JacksonDatatype.datatypeJsr310)
    implementation(project(":varsel-action"))

    testRuntimeOnly(Junit.engine)
    testImplementation(Junit.api)
    testImplementation(Mockk.mockk)
    testImplementation(TestContainers.postgresql)
    testImplementation(Kotest.runnerJunit5)
    testImplementation(Kotest.assertionsCore)
    testImplementation(Ktor.Test.serverTestHost)
    testImplementation(TmsKtorTokenSupport.tokenXValidationMock)
    testImplementation(TmsKtorTokenSupport.azureValidationMock)
}

application {
    mainClass.set("no.nav.tms.varsel.authority.ApplicationKt")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events("passed", "skipped", "failed")
        }
    }
}
