plugins {
    kotlin("jvm").version(Kotlin.version)
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
