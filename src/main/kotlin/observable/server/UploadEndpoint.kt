package observable.server

/**
 * Public upload endpoint for the Bomberbude Observable profile service.
 *
 * The service performs rate limiting, size checks and strict Observable-profile
 * validation server-side. No embedded credential or runtime string obfuscation
 * is used by the mod.
 */
internal object UploadEndpoint {
    private const val UPLOAD_URL =
        "https://obs.bombersbude.de/api.php?action=add"

    fun url(): String = UPLOAD_URL
}
