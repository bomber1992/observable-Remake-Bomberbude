package observable.server

internal object UploadEndpoint {
    private const val UPLOAD_KEY = "@OBSERVABLE_UPLOAD_KEY@"

    fun url(): String =
        "https://obs.bombersbude.de/api.php?action=add&key=$UPLOAD_KEY"
}
