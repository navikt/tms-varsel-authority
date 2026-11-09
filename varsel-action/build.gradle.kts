import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm").version(managed.versions.kotlin)

    `java-library`
    `maven-publish`
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    api(managed.jackson.annotations)

    testImplementation(managed.bundles.junit)
    testImplementation(managed.bundles.kotest)
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

val libraryVersion: String = providers.gradleProperty("lib_version")
	.orElse("latest-local").toString()

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
            artifactId = "varsel-action"
            version = libraryVersion
            from(components["java"])

            val sourcesJar by tasks.registering(Jar::class) {
                archiveClassifier.set("sources")
                from(sourceSets.main.get().allSource)
            }

            artifact(sourcesJar)
        }
    }
}
