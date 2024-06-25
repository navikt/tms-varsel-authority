object JacksonEx: default.JacksonDatatypeDefaults {
    val annotations = dependency("jackson-annotations", groupId = "com.fasterxml.jackson.core")
}
