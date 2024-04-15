plugins {
    kotlin("jvm").version(Kotlin.version)
}


kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks {
    jar {
        enabled = false
    }
}
