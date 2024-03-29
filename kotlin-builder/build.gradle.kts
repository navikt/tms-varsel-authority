import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm").version(Kotlin.version)
    kotlin("plugin.serialization") version Kotlin.version
    `java-library`
    `maven-publish`
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    api(project(":varsel-action"))
    implementation(JacksonDatatype.moduleKotlin)
    implementation(JacksonDatatype.datatypeJsr310)
    testImplementation(Junit.api)
    testImplementation(Junit.engine)
    testImplementation(Kotest.runnerJunit5)
    testImplementation(Kotest.assertionsCore)
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

val libraryVersion: String = properties["lib_version"]?.toString() ?: "latest-local"

publishing {
    repositories{
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/navikt/tms-varsel-authority")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("gpr") {
            groupId = "no.nav.tms.varsel"
            artifactId = "kotlin-builder"
            version = libraryVersion
            from(components["java"])
        }
    }
}
