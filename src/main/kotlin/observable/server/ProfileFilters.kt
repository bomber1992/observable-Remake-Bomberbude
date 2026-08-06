package observable.server

import java.util.Locale

/**
 * Sign block entities are intentionally omitted from profiling. They create
 * low-value visual clutter and are not useful performance targets for this mod.
 */
internal fun isIgnoredSignType(rawType: String): Boolean {
    val normalized = rawType.trim().lowercase(Locale.ROOT)
    val path = normalized.substringAfterLast(':').substringAfterLast('.')
    return path == "sign" || path == "hanging_sign" || path.endsWith("_sign")
}
