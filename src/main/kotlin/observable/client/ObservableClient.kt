package observable.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.common.NeoForge
import observable.Observable
import observable.net.C2SPacket
import observable.net.S2CPacket
import observable.server.ProfilingData
import observable.util.MOD_URL_COMPONENT
import observable.util.Marker
import org.lwjgl.glfw.GLFW
import java.net.URI

/** All classes and state which may only be loaded on the physical client. */
object ObservableClient {
    private val keyCategory = KeyMapping.Category(
        Identifier.fromNamespaceAndPath(Observable.MOD_ID, "keybinds")
    )

    val profileKeybind by lazy {
        KeyMapping(
            "key.observable.profile",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            keyCategory
        )
    }

    val profileScreen by lazy { ProfileScreen() }
    var results: ProfilingData? = null

    private val clientChat
        get() = Minecraft.getInstance().gui.chat

    @JvmStatic
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        event.registerCategory(keyCategory)
        event.register(profileKeybind)
    }

    @JvmStatic
    fun init() {
        registerPacketHandlers()
        NeoForge.EVENT_BUS.register(ClientEvents)
    }

    private fun registerPacketHandlers() {
        Observable.CHANNEL.register { packet: S2CPacket.ProfilingStarted, _ ->
            profileScreen.action = ProfileScreen.Action.TPSProfilerRunning(packet.endMillis)
            profileScreen.startBtn?.active = false
        }

        Observable.CHANNEL.register { _: S2CPacket.ProfilingCompleted, _ ->
            profileScreen.action = ProfileScreen.Action.TPSProfilerCompleted
        }

        Observable.CHANNEL.register { _: S2CPacket.ProfilerInactive, _ ->
            profileScreen.action = ProfileScreen.Action.DEFAULT
            profileScreen.startBtn?.active = true
        }

        Observable.CHANNEL.register { packet: S2CPacket.ProfilingResult, _ ->
            results = packet.data
            profileScreen.action = ProfileScreen.Action.DEFAULT
            profileScreen.startBtn?.active = true
            Overlay.loadSync()

            if (packet.link != null) {
                val linkText = Component.literal(packet.link)
                    .withStyle(ChatFormatting.UNDERLINE)
                    .withStyle { it.withClickEvent(ClickEvent.OpenUrl(URI.create(packet.link))) }
                clientChat.addClientSystemMessage(
                    Component.translatable("text.observable.profile_uploaded", linkText)
                )
            } else {
                clientChat.addClientSystemMessage(Component.translatable("text.observable.upload_failed"))
                clientChat.addClientSystemMessage(
                    Component.translatable("text.observable.profile_saved", ProfileExporter.export(packet.data))
                )
                clientChat.addClientSystemMessage(
                    Component.translatable("text.observable.after_save", MOD_URL_COMPONENT)
                )
            }
        }

        Observable.CHANNEL.register { packet: S2CPacket.Availability, _ ->
            when (packet) {
                S2CPacket.Availability.Available -> {
                    profileScreen.action = ProfileScreen.Action.DEFAULT
                    profileScreen.startBtn?.active = true
                }
                S2CPacket.Availability.NoPermissions -> {
                    profileScreen.action = ProfileScreen.Action.NO_PERMISSIONS
                    profileScreen.startBtn?.active = false
                }
            }
        }
    }

    object ClientEvents {
        private var lastLevel: Any? = null

        @SubscribeEvent
        fun onClientTick(event: ClientTickEvent.Post) {
            val minecraft = Minecraft.getInstance()
            if (profileKeybind.consumeClick()) minecraft.setScreen(profileScreen)

            val level = minecraft.level
            if (level != null && level !== lastLevel) {
                lastLevel = level
                Overlay.loadSync(level)
                Marker("observable_announce").mark {
                    clientChat.addClientSystemMessage(
                        Component.translatable("text.observable.announce", MOD_URL_COMPONENT)
                    )
                }
            }
        }

        @SubscribeEvent
        fun onClientLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
            Overlay.loadSync(Minecraft.getInstance().level)
            Observable.CHANNEL.sendToServer(C2SPacket.RequestAvailability)
        }

        @SubscribeEvent
        fun onClientLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
            results = null
            lastLevel = null
            profileScreen.action = ProfileScreen.Action.UNAVAILABLE
        }
    }
}
