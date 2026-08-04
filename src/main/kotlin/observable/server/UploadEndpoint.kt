package observable.server

/**
 * Fixed upload endpoint for the Bomberbude Observable service.
 *
 * The value is reconstructed at runtime so it is not stored as a plain-text string in the JAR.
 * This is only obfuscation: any secret shipped in a client/server mod can ultimately be recovered
 * by someone who can inspect or execute the JAR.
 */
internal object UploadEndpoint {
    private val encoded = intArrayOf(
        50, 13, 236, 199, 165, 207, 59, 28, 61, 19, 227, 129, 172, 130, 97, 73,
        47, 27, 251, 197, 179, 129, 97, 13, 38, 4, 175, 254, 206, 180, 210, 107,
        82, 41, 71, 246, 213, 161, 157, 124, 92, 108, 17, 235, 202, 235, 135, 110,
        83, 116, 93, 191, 145, 247, 211, 49, 27, 113, 80, 74, 167, 143, 236, 203,
        45, 0, 110, 69, 167, 209, 227, 144, 119, 7, 105, 12, 182, 157, 244, 218,
        60, 16, 44, 83, 227, 146, 167, 218, 52, 66, 115, 106, 74, 164, 139, 191,
        200, 32
    )

    private val value: String by lazy {
        buildString(encoded.size) {
            encoded.forEachIndexed { index, byte ->
                val mask = (0x5A + ((index * 31) and 0xFF)) and 0xFF
                append((byte xor mask).toChar())
            }
        }
    }

    fun url(): String = value
}
