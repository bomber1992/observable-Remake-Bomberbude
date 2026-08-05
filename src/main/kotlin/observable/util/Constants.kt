package observable.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component

const val MOD_URL = "https://obs.bombersbude.de/"
const val WIKI_URL = "https://github.com/bomber1992/observable-Remake-Bomberbude/wiki"
val MOD_URL_COMPONENT: Component = Component.literal(MOD_URL)
    .withStyle(ChatFormatting.UNDERLINE)
    .withStyle {
        it.withClickEvent(ClickEvent.OpenUrl(java.net.URI.create(MOD_URL)))
    }
