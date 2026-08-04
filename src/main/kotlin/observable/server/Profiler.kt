package observable.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.server.ServerLifecycleHooks
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.TickingBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import observable.Observable
import observable.Props
import observable.net.S2CPacket
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.schedule
import kotlin.random.Random

inline val StackTraceElement.classMethod
    get() = "${this.className} + ${this.methodName}"

class Profiler {
    private val stateLock = Any()
    data class TimingData(
        var time: Long,
        var ticks: Int,
        var traces: TraceMap,
        var name: String = ""
    )

    /**
     * Timing data for work that belongs to a logical device at a block position,
     * but is not executed through Minecraft's BlockEntity ticker. AE2 grid
     * devices are the primary example. The identity map keeps multiple parts in
     * the same cable-bus block separate.
     */
    data class PositionedTimingData(
        val dimension: ResourceKey<Level>,
        val position: BlockPos,
        val data: TimingData
    )

    var timingsMap = HashMap<Entity, TimingData>()
    lateinit var serverTraceMap: TraceMap
    lateinit var serverThread: Thread
    lateinit var samplerThread: Thread

    // TODO: consider splitting out block entity timings
    //    var blockEntityTimingsMap = HashMap<BlockEntity, TimingData>()
    var blockTimingsMap = HashMap<ResourceKey<Level>, HashMap<BlockPos, TimingData>>()

    // Identity is important here: two AE2 parts can share the same block position
    // and even the same registry id while still being independent ticking nodes.
    var externalBlockTimingsMap = IdentityHashMap<Any, PositionedTimingData>()

    var notProcessing
        get() = Props.notProcessing
        set(v) {
            Props.notProcessing = v
        }

    var player: ServerPlayer? = null
    var startTime: Long = 0
    var startingTicks: Int = 0

    fun process(entity: Entity) =
        timingsMap.getOrPut(entity) { TimingData(0, 0, TraceMap(entity::class)) }

    fun processBlockEntity(blockEntity: TickingBlockEntity, level: Level) =
        blockTimingsMap
            .getOrPut(level.dimension()) { HashMap() }
            .getOrPut(blockEntity.pos) {
                TimingData(
                    0,
                    0,
                    TraceMap(blockEntity::class),
                    blockEntity.type
                )
            }

    fun processBlock(blockState: BlockState, pos: BlockPos, level: Level) =
        blockTimingsMap
            .getOrPut(level.dimension()) { HashMap() }
            .getOrPut(pos) {
                TimingData(
                    0,
                    0,
                    TraceMap(blockState::class),
                    blockState.block.descriptionId
                )
            }

    fun processFluid(fluidState: FluidState, pos: BlockPos, level: Level) =
        blockTimingsMap
            .getOrPut(level.dimension()) { HashMap() }
            .getOrPut(pos) {
                TimingData(
                    0,
                    0,
                    TraceMap(fluidState::class),
                    BuiltInRegistries.FLUID.getKey(fluidState.type).toString()
                )
            }

    /** Returns an already registered logical-device timing without doing lookup work again. */
    fun findExternalBlockTiming(identity: Any): TimingData? =
        externalBlockTimingsMap[identity]?.data

    /**
     * Registers a logical ticking device at a world position. This is intentionally
     * generic so optional compatibility mixins do not leak third-party API types
     * into the always-loaded profiler class.
     */
    fun processExternalBlock(
        identity: Any,
        level: Level,
        position: BlockPos,
        name: String
    ): TimingData =
        externalBlockTimingsMap
            .getOrPut(identity) {
                PositionedTimingData(
                    level.dimension(),
                    position.immutable(),
                    TimingData(0, 0, TraceMap(identity::class), name)
                )
            }
            .data

    fun startRunning(sample: Boolean = false) {
        timingsMap.clear()
        blockTimingsMap.clear()
        externalBlockTimingsMap.clear()
        serverTraceMap = TraceMap()
        startTime = System.currentTimeMillis()
        synchronized(stateLock) {
            notProcessing = false
            startingTicks = requireNotNull(ServerLifecycleHooks.getCurrentServer()).tickCount
        }
        if (sample) {
            samplerThread = Thread(TaggedSampler(serverThread))
            samplerThread.start()

            Thread {
                while (!Props.notProcessing) {
                    val interval = ServerSettings.traceInterval.toLong()
                    val deviation = ServerSettings.deviation.toLong()
                    serverTraceMap.add(serverThread.stackTrace.reversed().iterator())
                    Thread.sleep(interval + Random.nextLong(-deviation, deviation))
                }
            }
                .start()
        }
    }

    fun runWithDuration(
        player: ServerPlayer?,
        duration: Int,
        sample: Boolean
    ) {
        this.player = player
        startRunning(sample)
        val durMs = duration.toLong() * 1000L
        Observable.CHANNEL.sendToPlayers(
            requireNotNull(ServerLifecycleHooks.getCurrentServer()).playerList.players,
            S2CPacket.ProfilingStarted(startTime + durMs)
        )
        Timer("Profiler", false).schedule(durMs) {
            stopRunning()
        }
    }

    fun uploadProfile(data: ProfilingData, diagnostics: JsonObject): String? {
        Observable.LOGGER.info("Attempting to upload profile")
        val serialized = Json.encodeToString(DataWithDiagnostics(data, diagnostics))

        return try {
            val conn = URL(UploadEndpoint.url()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true

            Observable.LOGGER.info("Writing ${String.format("%.2f", serialized.length / 1000.0)}kb")
            GZIPOutputStream(conn.outputStream).bufferedWriter(Charsets.UTF_8).use {
                it.write(serialized)
            }

            val profileURL = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Observable.LOGGER.info("Profile uploaded to $profileURL")

            profileURL
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopRunning() {
        val diagnostics = getDiagnostics()
        val ticks: Int
        synchronized(stateLock) {
            notProcessing = true
            ticks = requireNotNull(ServerLifecycleHooks.getCurrentServer()).tickCount - startingTicks
        }
        val players = player?.let { listOf(it) } ?: listOf()
        Observable.CHANNEL.sendToPlayers(players, S2CPacket.ProfilingCompleted)
        val data = ProfilingData.create(
            timingsMap,
            blockTimingsMap,
            externalBlockTimingsMap.values,
            ticks,
            serverTraceMap
        )
        Observable.LOGGER.info("Profiler ran for $ticks ticks, sending data")
        Observable.LOGGER.info("Sending to ${players.map { it.gameProfile.name }}")
        val link = uploadProfile(data, diagnostics)
        Observable.CHANNEL.sendToPlayersSplit(players, S2CPacket.ProfilingResult(data, link))
        Observable.LOGGER.info("Data transfer complete!")
        ServerLifecycleHooks.getCurrentServer()
            ?.playerList
            ?.players
            ?.filter { Observable.hasPermission(it) }
            ?.let { Observable.CHANNEL.sendToPlayers(it, S2CPacket.ProfilerInactive) }
    }
}
