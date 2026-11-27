plugins {
    alias(managed.plugins.kotlin)
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
