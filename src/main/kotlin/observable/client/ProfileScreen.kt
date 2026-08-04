package observable.client

import net.minecraft.util.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import observable.Observable
import observable.net.C2SPacket
import observable.util.WIKI_URL
import java.util.Locale
import kotlin.math.roundToInt

class ProfileScreen : Screen(Component.translatable("screen.observable.profile")) {

    sealed class Action {
        companion object {
            val DEFAULT = NewProfile(30)
            val UNAVAILABLE = ObservableStatus("text.observable.unavailable")
            val NO_PERMISSIONS = ObservableStatus("text.observable.no_permissions")
        }

        data class NewProfile(var duration: Int) : Action()

        data class TPSProfilerRunning(val endTime: Long) : Action()

        data object TPSProfilerCompleted : Action()

        data class ObservableStatus(val text: String) : Action()

        data class Custom(val text: String) : Action()

        val statusMsg
            get() =
                when (this) {
                    is NewProfile -> "Duration (scroll): $duration seconds"
                    is TPSProfilerRunning ->
                        "Running for another %.1f seconds"
                            .format(
                                ((endTime - System.currentTimeMillis()).toDouble() / 1e3).coerceAtLeast(
                                    0.0
                                )
                            )
                    is TPSProfilerCompleted -> "Profiling finished, please wait..."
                    is ObservableStatus -> Component.translatable(text).string
                    is Custom -> text
                }
    }

    var action: Action = Action.UNAVAILABLE
    var startBtn: Button? = null
    var sample = false

    private fun updateStartButtonText() {
        val message =
            when (val currentAction = action) {
                is Action.TPSProfilerRunning -> {
                    val remainingSeconds =
                        ((currentAction.endTime - System.currentTimeMillis()).toDouble() / 1_000.0)
                            .coerceAtLeast(0.0)
                    Component.literal(
                        "Timer: ${String.format(Locale.ROOT, "%.1f", remainingSeconds)} s"
                    )
                }
                is Action.TPSProfilerCompleted -> Component.literal("Uploading...")
                else -> Component.translatable("text.observable.profile_tps")
            }

        startBtn?.setMessage(message)
    }

    private fun openLink(dest: String) {
        val mc = Minecraft.getInstance()
        mc.setScreen(
            ConfirmLinkScreen(
                { bl: Boolean ->
                    if (bl) {
                        Util.getPlatform().openUri(dest)
                    }
                    mc.setScreen(this)
                },
                dest,
                true
            )
        )
    }

    private fun button(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        component: Component,
        onPress: () -> Unit
    ): Button {
        val btn = Button.builder(component) { onPress() }.pos(x, y).size(width, height).build()
        return addRenderableWidget(btn)
    }

    override fun init() {
        super.init()

        val startBtn =
            button(
                0,
                height / 2 - 48,
                100,
                20,
                Component.translatable("text.observable.profile_tps")
            ) {
                val newProfile = action as? Action.NewProfile ?: return@button
                val duration = newProfile.duration

                // Start the visible countdown immediately; the server packet replaces this
                // with its authoritative end timestamp as soon as it is received.
                action = Action.TPSProfilerRunning(System.currentTimeMillis() + duration * 1_000L)
                this.startBtn?.active = false
                updateStartButtonText()
                Observable.CHANNEL.sendToServer(C2SPacket.InitTPSProfile(duration, sample))
            }
        startBtn.active = action is Action.NewProfile
        startBtn.x = width / 2 - startBtn.width - 4

        val settingsBtn =
            button(
                width / 2 + 4,
                startBtn.y,
                startBtn.width,
                startBtn.height,
                Component.translatable("screen.observable.client_settings")
            ) {
                Minecraft.getInstance().setScreen(ClientSettingsGui())
            }

        val samplerBtn =
            addRenderableWidget(
                BetterCheckbox(
                    startBtn.x,
                    startBtn.y + startBtn.height + 4,
                    settingsBtn.x + settingsBtn.width - startBtn.x,
                    20,
                    Component.translatable("text.observable.sampler"),
                    sample
                ) {
                    sample = it
                }
            )

        val longWidth = settingsBtn.x + settingsBtn.width - samplerBtn.x
        val bottomButtonWidth = (longWidth - 4) / 2

        val overlayBtn =
            addRenderableWidget(
                BetterCheckbox(
                    samplerBtn.x,
                    samplerBtn.y + samplerBtn.height + 4,
                    samplerBtn.width,
                    20,
                    Component.translatable("text.observable.overlay"),
                    Overlay.enabled
                ) {
                    if (it) {
                        synchronized(Overlay) { Overlay.load() }
                    }
                    Overlay.enabled = it
                }
            )

        val learnBtn =
            button(
                startBtn.x,
                overlayBtn.y + overlayBtn.height + 8,
                bottomButtonWidth,
                20,
                Component.translatable("text.observable.docs")
            ) {
                openLink(WIKI_URL)
            }
        button(
            learnBtn.x + learnBtn.width + 4,
            learnBtn.y,
            longWidth - learnBtn.width - 4,
            20,
            Component.translatable("text.observable.website")
        ) {
            openLink("https://bomberbude.de/")
        }

        this.startBtn = startBtn
        updateStartButtonText()
        Observable.CHANNEL.sendToServer(C2SPacket.RequestAvailability)
    }

    override fun tick() {
        super.tick()
        updateStartButtonText()
    }

    override fun isPauseScreen() = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, i: Int, j: Int, f: Float) {
        super.extractRenderState(graphics, i, j, f)

        graphics.centeredText(
            this.font,
            action.statusMsg,
            width / 2,
            startBtn!!.y - this.font.lineHeight - 4,
            0xFFFFFFFF.toInt()
        )
    }

    override fun mouseScrolled(d: Double, e: Double, f: Double, g: Double): Boolean {
        (action as? Action.NewProfile)?.apply {
            duration += g.roundToInt() * 5
            duration = this.duration.coerceIn(5, 60)
        }

        return super.mouseScrolled(d, e, f, g)
    }
}
