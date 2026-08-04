package observable

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import observable.net.BetterChannel
import observable.net.C2SPacket
import observable.net.S2CPacket
import observable.server.OBSERVABLE_COMMAND
import observable.server.Profiler
import observable.server.ServerSettings
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import net.minecraft.resources.Identifier

/** Common/server-safe Observable entry point. Client-only state lives in ObservableClient. */
object Observable {
    const val MOD_ID = "observable"

    val CHANNEL = BetterChannel(Identifier.fromNamespaceAndPath(MOD_ID, "channel"))
    val LOGGER: Logger = LogManager.getLogger("Observable - Remake")
    val PROFILER: Profiler by lazy { Profiler() }

    fun hasPermission(player: Player): Boolean {
        if (ServerSettings.allPlayersAllowed) return true
        if (ServerSettings.allowedPlayers.contains(player.gameProfile.id.toString())) return true
        val server = ServerLifecycleHooks.getCurrentServer() ?: return false
        if (server.playerList.isOp(player.nameAndId())) return true
        return server.isSingleplayer
    }

    /** Registers packet consumers which are valid on the physical server. */
    @JvmStatic
    fun init() {
        CHANNEL.register { packet: C2SPacket.InitTPSProfile, context ->
            val player = context.player
            if (player == null || !hasPermission(player)) {
                LOGGER.info("${player?.name?.string ?: "Unknown player"} lacks permissions to start profiling")
                return@register
            }
            if (PROFILER.notProcessing) {
                PROFILER.runWithDuration(player as? ServerPlayer, packet.duration, packet.sample)
            }
            LOGGER.info("${player.gameProfile.name} started profiler for ${packet.duration} s")
        }

        CHANNEL.register { _: C2SPacket.RequestAvailability, context ->
            (context.player as? ServerPlayer)?.let { player ->
                CHANNEL.sendToPlayer(
                    player,
                    if (hasPermission(player)) S2CPacket.Availability.Available
                    else S2CPacket.Availability.NoPermissions
                )
            }
        }
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        PROFILER.serverThread = event.server.runningThread
        LOGGER.info("Registered thread ${PROFILER.serverThread.name}")
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.register(OBSERVABLE_COMMAND)
    }
}
