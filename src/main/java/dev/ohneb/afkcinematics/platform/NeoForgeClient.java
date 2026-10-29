package dev.ohneb.afkcinematics.platform;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.ClientCommands;
import dev.ohneb.afkcinematics.client.SettingsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = AfkCinematics.ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus bus, ModContainer container) {
        AfkCinematics.initialize(FMLPaths.CONFIGDIR.get(), ModList.get()::isLoaded);
        bus.addListener(NeoForgeClient::keys);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::tick);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient::commands);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
    }

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(AfkCinematics.TOGGLE);
        event.register(AfkCinematics.SETTINGS);
        event.register(AfkCinematics.NEXT);
    }
    private static void tick(ClientTickEvent.Post event) { AfkCinematics.tick(); }
    private static void commands(RegisterClientCommandsEvent event) { event.getDispatcher().register(ClientCommands.root()); }
}
