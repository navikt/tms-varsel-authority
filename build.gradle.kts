plugins {
    kotlin("jvm").version(managed.versions.kotlin)
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    jar {
        enabled = false
    }
}
