plugins {
    kotlin("jvm").version(libs.versions.kotlin)
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
