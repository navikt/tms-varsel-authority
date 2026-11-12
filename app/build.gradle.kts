import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm").version(managed.versions.kotlin)

    alias(managed.plugins.tms.jarBundling)

    // Apply the application plugin to add support for building a CLI application.
    application
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
    implementation(project(":varsel-action"))

    implementation(managed.bundles.ktorServer)
    implementation(managed.bundles.logback)
    implementation(managed.bundles.jackson)

    implementation(managed.flyway.postgres)
    implementation(managed.hikariCp)
    implementation(managed.kafka.clients)
    implementation(managed.kotlinLogging)
    implementation(managed.kotliQuery)
    implementation(managed.ktor.serialization.jackson)
    implementation(managed.ktor.server.contentNegotiation)
    implementation(managed.postgresql)
    implementation(managed.prometheus.metricsCore)
    implementation(managed.tms.commonLib.kubernetes)
    implementation(managed.tms.commonLib.metrics)
    implementation(managed.tms.commonLib.observability)
    implementation(managed.tms.commonLib.postgres)
    implementation(managed.tms.commonLib.teamLogger)
    implementation(managed.tms.commonLib.utils)
    implementation(managed.tms.kafka.application)
    implementation(managed.tms.kafka.producerUtils)
    implementation(managed.tms.ktorTokenSupport.userToken.verification)
    implementation(managed.tms.ktorTokenSupport.entraId.verification)

    testImplementation(managed.bundles.junit)
    testImplementation(managed.bundles.kotest)

    testImplementation(managed.ktor.client.contentNegotiation)
    testImplementation(managed.ktor.test.server)
    testImplementation(managed.mockk)
    testImplementation(managed.testContainers.postgresql)
    testImplementation(managed.tms.ktorTokenSupport.userToken.verificationMock)
    testImplementation(managed.tms.ktorTokenSupport.entraId.verificationMock)
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
