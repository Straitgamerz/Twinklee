object BuildConfig {
    const val MINECRAFT_VERSION: String = "1.21.1"
    const val FABRIC_LOADER_VERSION: String = "0.16.10"
    const val NEOFORGE_VERSION: String = "21.1.172"
    const val FABRIC_API_VERSION: String = "0.102.0+1.21.1"
    const val UKULIB_VERSION: String = "1.4.1"

    const val MOD_VERSION: String = "2.5.0"

    const val MODRINTH_PROJECT_ID: String = "dpkYdLu5"

    fun createVersionString(): String {
        return "$MOD_VERSION+mc$MINECRAFT_VERSION"
    }
}
