package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class LensGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        int previousFov = context.computeOnClient(client -> client.options.fov().get());
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                floor(server.getPlayerList().getPlayers().getFirst());
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(20);
            context.runOnClient(client -> {
                client.options.fov().set(100);
                AfkCinematics.config().lens = CinematicConfig.Lens.CINEMATIC;
            });
            double hand = context.computeOnClient(client -> fieldOfView(client, false));
            start(context);
            context.waitTicks(100);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Cinematic stopped during lens entry");
                require(Math.abs(fieldOfView(client, true) - 62) < 0.001, "World projection did not use the cinematic lens");
                require(fieldOfView(client, false) == hand, "The lens changed the hand projection");
                require(client.options.fov().get() == 100, "The lens changed the FOV option");
                AfkCinematics.config().lens = CinematicConfig.Lens.GAME;
                require(Math.abs(fieldOfView(client, true) - 100) < 0.001, "Game lens did not preserve the normal FOV");
                AfkCinematics.config().lens = CinematicConfig.Lens.WIDE;
                require(Math.abs(fieldOfView(client, true) - 78) < 0.001, "Wide lens did not reach its fixed FOV");
                AfkCinematics.stop();
                require(Math.abs(fieldOfView(client, true) - 100) < 0.001, "Stopping retained the cinematic lens");
                require(client.options.fov().get() == 100, "Stopping changed the FOV option");
            });
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().lens = CinematicConfig.Lens.CINEMATIC;
                client.options.fov().set(previousFov);
                focused = null;
            });
        }
    }

    private static double fieldOfView(Minecraft client, boolean world) {
        try {
            //? if >=26.1 {
            /*var method = Camera.class.getDeclaredMethod(world ? "calculateFov" : "calculateHudFov", float.class);
            method.setAccessible(true);
            return ((Number)method.invoke(camera(client), 1F)).doubleValue();
            *///?} else {
            var method = client.gameRenderer.getClass().getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
            method.setAccessible(true);
            return ((Number)method.invoke(client.gameRenderer, camera(client), 1F, world)).doubleValue();
            //?}
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
