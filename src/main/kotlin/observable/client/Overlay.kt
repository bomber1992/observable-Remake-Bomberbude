package observable.client

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * Profiling heat-map overlay, implemented with the 26.1 gizmo renderer.
 * The overlay retains through-wall boxes and billboard labels while avoiding
 * private RenderType reflection from older Minecraft versions.
 */
object Overlay {
    data class Color(val r: Int, val g: Int, val b: Int, val a: Int) {
        companion object {
            fun fromNanos(rateNanos: Double) = Color(rateNanos / 1000.0)
        }

        constructor(rateMicros: Double) : this(
            (rateMicros / 100.0 * 255).roundToInt().coerceIn(0, 255),
            ((100.0 - rateMicros) / 100.0 * 255).roundToInt().coerceIn(0, 255),
            0,
            (rateMicros / 100.0 * 255).roundToInt().coerceIn(20, 100)
        )

        val opaqueArgb: Int get() = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        val translucentArgb: Int get() = (a shl 24) or (r shl 16) or (g shl 8) or b

        // Original Observable brightened entity-label colors so they stay readable
        // through walls while preserving the green -> yellow -> red heat scale.
        val labelArgb: Int
            get() {
                val red = if (r > g) 255 else if (g == 0) 0 else 255 * r / g
                val green = if (g > r) 255 else if (r == 0) 0 else 255 * g / r
                return (0xFF shl 24) or (red shl 16) or (green shl 8) or b
            }
    }

    sealed class Entry(val color: Color) {
        data class EntityEntry(val entityId: Int, val rate: Double) : Entry(Color.fromNanos(rate)) {
            val entity get() = Minecraft.getInstance().level?.getEntity(entityId)
        }

        data class BlockEntry(val pos: BlockPos, val rate: Double) : Entry(Color.fromNanos(rate))
    }

    @Volatile var enabled = true
    @Volatile var entities: List<Entry.EntityEntry> = emptyList()
    @Volatile var blocks: List<Entry.BlockEntry> = emptyList()
    @Volatile var blockMap: Map<ChunkPos, List<Entry.BlockEntry>> = emptyMap()

    fun load(levelOverride: ClientLevel? = null) {
        val data = ObservableClient.results ?: return
        val level = levelOverride ?: Minecraft.getInstance().level ?: return
        val levelLocation = level.dimension().identifier()
        val ticks = data.ticks.coerceAtLeast(1)
        val normalize = ClientSettings.normalized

        entities = data.entities[levelLocation]
            .orEmpty()
            .asSequence()
            .mapNotNull { entry ->
                entry.entityId?.let { id ->
                    Entry.EntityEntry(
                        id,
                        entry.rate * if (normalize) entry.ticks.toDouble() / ticks else 1.0
                    )
                }
            }
            .filter { it.rate >= ClientSettings.minRate }
            .sortedByDescending { it.rate }
            .toList()

        blocks = data.blocks[levelLocation]
            .orEmpty()
            .asSequence()
            .map { entry ->
                Entry.BlockEntry(
                    entry.position,
                    entry.rate * if (normalize) entry.ticks.toDouble() / ticks else 1.0
                )
            }
            .filter { it.rate >= ClientSettings.minRate }
            .sortedByDescending { it.rate }
            .toList()

        blockMap = blocks.groupBy { ChunkPos.containing(it.pos) }
    }

    fun loadSync(level: ClientLevel? = null) = synchronized(this) { load(level) }

    /** Adds this frame's boxes and labels to Minecraft's native gizmo collector. */
    fun collect() {
        if (!enabled || ObservableClient.results == null) return
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val camera = minecraft.gameRenderer.mainCamera
        val cameraPos = camera.position()

        minecraft.levelRenderer.collectPerFrameGizmos().use {
            synchronized(this) {
                collectBlocks(player.blockPosition(), cameraPos)
                collectEntities(cameraPos)
            }
        }
    }

    private fun collectBlocks(playerPos: BlockPos, cameraPos: Vec3) {
        val chunk = ChunkPos.containing(playerPos)
        val chunkDistance = (ClientSettings.maxBlockDist / 16).coerceAtLeast(2)
        val maxDistanceSqr = ClientSettings.maxBlockDist.toDouble() * ClientSettings.maxBlockDist
        var rendered = 0

        for (x in (chunk.x - chunkDistance)..(chunk.x + chunkDistance)) {
            for (z in (chunk.z - chunkDistance)..(chunk.z + chunkDistance)) {
                for (entry in blockMap[ChunkPos(x, z)].orEmpty()) {
                    if (rendered >= ClientSettings.maxBlockCount) return
                    if (Vec3.atCenterOf(entry.pos).distanceToSqr(cameraPos) > maxDistanceSqr) continue

                    val style = GizmoStyle.strokeAndFill(
                        entry.color.opaqueArgb,
                        1.5F,
                        entry.color.translucentArgb
                    )
                    Gizmos.cuboid(entry.pos, style).setAlwaysOnTop()
                    Gizmos.billboardText(
                        formatRate(entry.rate),
                        Vec3.atCenterOf(entry.pos),
                        TextGizmo.Style.forColorAndCentered(0xFFFFFFFF.toInt()).withScale(0.75F)
                    ).setAlwaysOnTop()
                    rendered++
                }
            }
        }
    }

    private fun collectEntities(cameraPos: Vec3) {
        val maxDistanceSqr = ClientSettings.maxEntityDist.toDouble() * ClientSettings.maxEntityDist
        for (entry in entities.take(ClientSettings.maxEntityCount)) {
            val entity = entry.entity ?: continue
            if (entity.isRemoved) continue
            val center = entity.boundingBox.center
            if (center.distanceToSqr(cameraPos) > maxDistanceSqr) continue

            // Player hitboxes are especially intrusive in first/third person and can
            // cover the screen. Keep the coloured µs/t label, but only draw boxes for
            // non-player entities.
            if (entity !is Player) {
                val style = GizmoStyle.strokeAndFill(
                    entry.color.opaqueArgb,
                    1.5F,
                    entry.color.translucentArgb
                )
                Gizmos.cuboid(entity.boundingBox.inflate(0.025), style).setAlwaysOnTop()
            }

            val deadSuffix = if (entity.isAlive) "" else " [X]"
            Gizmos.billboardText(
                formatRate(entry.rate) + deadSuffix,
                entity.position().add(0.0, entity.bbHeight.toDouble() + 0.33, 0.0),
                TextGizmo.Style.forColorAndCentered(entry.color.labelArgb).withScale(0.75F)
            ).setAlwaysOnTop()
        }
    }

    private fun formatRate(rateNanos: Double): String {
        val unit = if (ClientSettings.normalized) "μs/t" else "μs/call"
        return "${(rateNanos / 1000.0).roundToInt()} $unit"
    }
}
