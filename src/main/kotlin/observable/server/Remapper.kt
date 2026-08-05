package observable.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import observable.Observable

/** NeoForge uses the Forge/MCP method mapping set. */
enum class ModLoader {
    FORGE
}

private const val MAPPING_RESOURCE = "/observable/mappings/1_21_11/mcp.json"

inline val JsonElement?.stringMap
    get() = this?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: mapOf()

object Remapper {
    data class RemappingData(val classes: Map<String, String>, val methods: Map<String, String>)

    lateinit var modLoader: ModLoader

    val remappingData by lazy {
        try {
            check(modLoader == ModLoader.FORGE) { "Unsupported mod loader: $modLoader" }
            val jsonText = requireNotNull(Remapper::class.java.getResourceAsStream(MAPPING_RESOURCE)) {
                "Bundled profiler mappings are missing: $MAPPING_RESOURCE"
            }.bufferedReader(Charsets.UTF_8).use { it.readText() }

            val jsonData = Json.parseToJsonElement(jsonText).jsonObject
            RemappingData(
                classes = mapOf(),
                methods = jsonData["methods"].stringMap
            )
        } catch (e: Exception) {
            Observable.LOGGER.warn("Bundled profiling mappings could not be loaded: ${e.message}")
            Observable.LOGGER.warn("Remapping data will be unavailable for the remainder of the session")
            RemappingData(mapOf(), mapOf())
        }
    }

    fun transform(map: TraceMap) {
        remappingData.classes[map.className]?.let { map.className = it }
        remappingData.methods[map.methodName]?.let { map.methodName = it }
    }
}
