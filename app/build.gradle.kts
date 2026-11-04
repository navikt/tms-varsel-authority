import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm").version(libs.versions.kotlin)

    alias(libs.plugins.tms.jarBundling)

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

    implementation(libs.bundles.ktorServer)
    implementation(libs.bundles.logback)
    implementation(libs.bundles.jackson)

    implementation(libs.flyway.postgres)
    implementation(libs.hikariCp)
    implementation(libs.kafka.clients)
    implementation(libs.kotlinLogging)
    implementation(libs.kotliQuery)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.postgresql)
    implementation(libs.prometheus.metricsCore)
    implementation(libs.tms.commonLib.kubernetes)
    implementation(libs.tms.commonLib.metrics)
    implementation(libs.tms.commonLib.observability)
    implementation(libs.tms.commonLib.postgres)
    implementation(libs.tms.commonLib.teamLogger)
    implementation(libs.tms.commonLib.utils)
    implementation(libs.tms.kafka.application)
    implementation(libs.tms.kafka.producerUtils)
    implementation(libs.tms.ktorTokenSupport.userToken.verification)
    implementation(libs.tms.ktorTokenSupport.entraId.verification)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.kotest)

    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.ktor.test.server)
    testImplementation(libs.mockk)
    testImplementation(libs.testContainers.postgresql)
    testImplementation(libs.tms.ktorTokenSupport.userToken.verificationMock)
    testImplementation(libs.tms.ktorTokenSupport.entraId.verificationMock)
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
