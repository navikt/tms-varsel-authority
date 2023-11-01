import default.DependencyGroup

object RapidsAndRiversLatest: default.RapidsAndRiversDefaults {
    override val version get() = "2023080113411690890096.310ed8e5ed93"
}

object JacksonEx: default.JacksonDatatypeDefaults {
    val annotations = dependency("jackson-annotations", groupId = "com.fasterxml.jackson.core")
}
