package observable.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.SharedConstants
import net.minecraft.SystemReport
import net.neoforged.fml.ModList
import observable.Observable

fun Profiler.getDiagnostics(): JsonObject {
    val duration = System.currentTimeMillis() - startTime
    val systemReport = SystemReport()
    if (!ServerSettings.includeJvmArgs) {
        systemReport.setDetail("JVM Flags", "<REDACTED>")
    }

    val mods = ModList.get().mods
    val observableVersion = mods.firstOrNull { it.modId == Observable.MOD_ID }?.version?.toString() ?: "unknown"

    val triggeringProfile = player?.gameProfile

    return buildJsonObject {
        // Keep the UUID for diagnostics/legacy consumers, but expose the actual
        // in-game name separately so the web viewer never has to treat a UUID
        // as the player label.
        put("username", triggeringProfile?.name)
        put("user", triggeringProfile?.id?.toString())
        put("start", startTime)
        put("duration", duration)
        put("minecraftVersion", SharedConstants.getCurrentVersion().name())
        put("modLoader", "neoforge")
        put("observableVersion", observableVersion)
        put(
            "additionalDiagnostics",
            buildJsonObject {
                put("System Report", systemReport.toLineSeparatedString())
                put(
                    "Mods",
                    mods.joinToString("\n") { mod ->
                        "'${mod.displayName}' (version: ${mod.version})"
                    }
                )
            }
        )
    }
}
