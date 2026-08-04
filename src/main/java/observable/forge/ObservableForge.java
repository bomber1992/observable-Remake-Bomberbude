package observable.forge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import observable.Observable;
import observable.client.ObservableClient;
import observable.server.ModLoader;
import observable.server.Remapper;

@Mod(Observable.MOD_ID)
public final class ObservableForge {
    public ObservableForge(IEventBus modEventBus, ModContainer modContainer) {
        Remapper.INSTANCE.setModLoader(ModLoader.FORGE);
        Observable.init();

        modEventBus.addListener(Observable.INSTANCE.getCHANNEL()::registerPayloads);
        NeoForge.EVENT_BUS.register(Observable.INSTANCE);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(ObservableClient::registerKeyMappings);
            ObservableClient.init();
            NeoForge.EVENT_BUS.register(ForgeClientHooks.INSTANCE);
        }
    }
}
