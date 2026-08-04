package observable.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.NetworkRegistry
import net.neoforged.neoforge.network.handling.IPayloadContext
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * NeoForge-native replacement for Architectury's transformed channel.
 * Payloads retain the original protobuf encoding, gzip compression and splitting
 * so large profiling results remain transferable.
 */
@OptIn(ExperimentalSerializationApi::class)
class BetterChannel(val id: Identifier) {
    companion object {
        private const val NETWORK_VERSION = "2"
        const val MAX_CHUNK_SIZE = 900_000
        val LOGGER = LogManager.getLogger("ObservableNet")
    }

    enum class Side { C2S, S2C }

    data class PacketContext(val player: Player?, val native: IPayloadContext)

    val s2cLocation: Identifier = id.withSuffix("-s2c")
    val c2sLocation: Identifier = id.withSuffix("-c2s")
    @PublishedApi
    internal val s2cType = CustomPacketPayload.Type<SerializedPayload>(s2cLocation)
    @PublishedApi
    internal val c2sType = CustomPacketPayload.Type<SerializedPayload>(c2sLocation)

    class SerializedPayload(
        val className: String,
        val messageId: UUID,
        val chunkIndex: Int,
        val chunkCount: Int,
        val data: ByteArray,
        private val payloadType: CustomPacketPayload.Type<SerializedPayload>
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = payloadType
    }

    private data class PendingMessage(
        val className: String,
        val chunks: Array<ByteArray?>,
        var received: Int = 0,
        var touchedAt: Long = System.currentTimeMillis()
    )

    @PublishedApi
    internal val handlers = ConcurrentHashMap<String, (ByteArray, PacketContext) -> Unit>()
    private val pendingC2S = ConcurrentHashMap<UUID, PendingMessage>()
    private val pendingS2C = ConcurrentHashMap<UUID, PendingMessage>()

    private fun codec(type: CustomPacketPayload.Type<SerializedPayload>) =
        object : StreamCodec<RegistryFriendlyByteBuf, SerializedPayload> {
            override fun decode(buf: RegistryFriendlyByteBuf): SerializedPayload =
                SerializedPayload(
                    buf.readUtf(512),
                    buf.readUUID(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readByteArray(MAX_CHUNK_SIZE + 16_384),
                    type
                )

            override fun encode(buf: RegistryFriendlyByteBuf, payload: SerializedPayload) {
                buf.writeUtf(payload.className, 512)
                buf.writeUUID(payload.messageId)
                buf.writeVarInt(payload.chunkIndex)
                buf.writeVarInt(payload.chunkCount)
                buf.writeByteArray(payload.data)
            }
        }

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        // Optional payloads let vanilla/NeoForge clients without Observable join the server.
        // The feature channel is negotiated only when both sides provide it.
        val registrar = event.registrar(NETWORK_VERSION).optional()
        registrar.playToServer(c2sType, codec(c2sType)) { payload, context ->
            accept(payload, context, Side.C2S)
        }
        registrar.playToClient(s2cType, codec(s2cType)) { payload, context ->
            accept(payload, context, Side.S2C)
        }
    }

    private fun accept(payload: SerializedPayload, context: IPayloadContext, side: Side) {
        val pending = if (side == Side.C2S) pendingC2S else pendingS2C
        cleanupStale(pending)
        require(payload.chunkCount in 1..65_536) { "Invalid Observable chunk count ${payload.chunkCount}" }
        require(payload.chunkIndex in 0 until payload.chunkCount) { "Invalid Observable chunk index ${payload.chunkIndex}" }

        val message = pending.compute(payload.messageId) { _, existing ->
            val target = existing ?: PendingMessage(payload.className, arrayOfNulls(payload.chunkCount))
            require(target.className == payload.className && target.chunks.size == payload.chunkCount) {
                "Inconsistent Observable split payload"
            }
            if (target.chunks[payload.chunkIndex] == null) {
                target.chunks[payload.chunkIndex] = payload.data
                target.received++
            }
            target.touchedAt = System.currentTimeMillis()
            target
        } ?: return

        if (message.received == message.chunks.size) {
            pending.remove(payload.messageId)
            val compressed = ByteArrayOutputStream().use { output ->
                message.chunks.forEach { output.write(requireNotNull(it)) }
                output.toByteArray()
            }
            val decoded = GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readAllBytes() }
            handlers[payload.className]?.invoke(decoded, PacketContext(context.player(), context))
                ?: LOGGER.warn("No handler registered for ${payload.className}")
        }
    }

    private fun cleanupStale(messages: ConcurrentHashMap<UUID, PendingMessage>) {
        val cutoff = System.currentTimeMillis() - 60_000L
        messages.entries.removeIf { it.value.touchedAt < cutoff }
    }

    inline fun <reified T> register(noinline consumer: (T, PacketContext) -> Unit) {
        handlers[T::class.java.name] = { bytes, context ->
            consumer(ProtoBuf.decodeFromByteArray(bytes), context)
        }
        LOGGER.info("Registered ${T::class.java.name}")
    }

    @PublishedApi
    internal inline fun <reified T> createPayloads(message: T, side: Side): List<SerializedPayload> {
        val encoded = ProtoBuf.encodeToByteArray(message)
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(encoded) }
            output.toByteArray()
        }
        val count = ((compressed.size + MAX_CHUNK_SIZE - 1) / MAX_CHUNK_SIZE).coerceAtLeast(1)
        val uuid = UUID.randomUUID()
        val type = if (side == Side.S2C) s2cType else c2sType
        return (0 until count).map { index ->
            val start = index * MAX_CHUNK_SIZE
            val end = minOf(compressed.size, start + MAX_CHUNK_SIZE)
            SerializedPayload(T::class.java.name, uuid, index, count, compressed.copyOfRange(start, end), type)
        }
    }

    @PublishedApi
    internal fun supportsClient(player: ServerPlayer): Boolean =
        NetworkRegistry.hasChannel(player.connection, s2cLocation)

    @PublishedApi
    internal fun supportsServer(): Boolean {
        val listener = net.minecraft.client.Minecraft.getInstance().connection ?: return false
        return NetworkRegistry.hasChannel(listener, c2sLocation)
    }

    inline fun <reified T> sendToPlayers(players: Iterable<ServerPlayer>, msg: T) {
        val supportedPlayers = players.filter(::supportsClient)
        if (supportedPlayers.isEmpty()) return

        val payloads = createPayloads(msg, Side.S2C)
        supportedPlayers.forEach { player ->
            payloads.forEach { PacketDistributor.sendToPlayer(player, it) }
        }
    }

    inline fun <reified T> sendToPlayer(player: ServerPlayer, msg: T) = sendToPlayers(listOf(player), msg)
    inline fun <reified T> sendToPlayersSplit(players: Iterable<ServerPlayer>, msg: T) = sendToPlayers(players, msg)

    inline fun <reified T> sendToServer(msg: T) {
        if (!supportsServer()) {
            LOGGER.debug("Observable server channel is unavailable; skipping ${T::class.java.name}")
            return
        }
        createPayloads(msg, Side.C2S).forEach(ClientPacketDistributor::sendToServer)
    }
}
