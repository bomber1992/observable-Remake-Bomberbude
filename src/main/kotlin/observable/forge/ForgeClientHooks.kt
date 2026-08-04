package observable.forge

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import observable.client.Overlay

object ForgeClientHooks {
    @SubscribeEvent
    fun onRender(event: RenderLevelStageEvent.AfterTranslucentFeatures) {
        Overlay.collect()
    }
}
