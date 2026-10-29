package dev.ohneb.afkcinematics.platform;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.ClientCommands;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        AfkCinematics.initialize(loader.getConfigDir(), loader::isModLoaded);
        KeyBindingHelper.registerKeyBinding(AfkCinematics.TOGGLE);
        KeyBindingHelper.registerKeyBinding(AfkCinematics.SETTINGS);
        KeyBindingHelper.registerKeyBinding(AfkCinematics.NEXT);
        ClientTickEvents.END_CLIENT_TICK.register(client -> AfkCinematics.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(ClientCommands.root()));
    }
}
