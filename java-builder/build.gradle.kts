import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    `maven-publish`
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    api(project(":varsel-action"))
    implementation(JacksonDatatype.datatypeJsr310)
    testImplementation(Junit.api)
    testImplementation(Junit.engine)
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
            artifactId = "java-builder"
            version = libraryVersion
            from(components["java"])

            tasks.creating(Jar::class) {
                archiveClassifier.set("sources")
                from(sourceSets.main.get().allSource)
            }.let { sourcesJar ->
                artifact(sourcesJar)
            }
        }
    }
}
