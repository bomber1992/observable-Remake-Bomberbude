package observable.server

import net.neoforged.fml.loading.FMLPaths
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

val configFile = FMLPaths.CONFIGDIR.get().resolve("observable.json")
private val settingsJson = Json { ignoreUnknownKeys = true }
var ServerSettings = loadSettings()

@Serializable
data class ServerSettingsData(
    var traceInterval: Int = 3,
    var deviation: Int = 1,
    var notifyInterval: Int = 120 * 60 * 1000,
    var allPlayersAllowed: Boolean = false,
    var allowedPlayers: MutableSet<String> = mutableSetOf(),
    var includeJvmArgs: Boolean = true
) {
    fun sync() = configFile.writeText(settingsJson.encodeToString(this))
}

fun loadSettings(): ServerSettingsData {
    if (!configFile.exists()) {
        val settings = ServerSettingsData()
        settings.sync()
        return settings
    }

    // Ignore and remove legacy fields such as uploadURL. The endpoint is fixed in the mod now.
    val settings = settingsJson.decodeFromString<ServerSettingsData>(configFile.readText())
    settings.sync()
    return settings
}

fun resetSettings() {
    configFile.deleteIfExists()
    ServerSettings = loadSettings()
}
