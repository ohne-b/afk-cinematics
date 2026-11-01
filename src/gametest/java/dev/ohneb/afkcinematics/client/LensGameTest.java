package dev.ohneb.afkcinematics.client;

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
                require(AfkCinematics.config().useGameFov, "Fresh settings did not use the game FOV");
            });
            double hand = context.computeOnClient(client -> fieldOfView(client, false));
            start(context);
            context.waitTicks(140);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Cinematic stopped during lens entry");
                require(Math.abs(fieldOfView(client, true) - 100) < 0.001, "Default lens did not preserve the game FOV");
                AfkCinematics.config().useGameFov = false;
                AfkCinematics.config().customFov = 140;
            });
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Cinematic stopped at the widest custom FOV");
                require(Math.abs(fieldOfView(client, true) - 140) < 0.001, "Custom FOV did not exceed the vanilla slider range");
                require(fieldOfView(client, false) == hand, "The lens changed the hand projection");
                require(client.options.fov().get() == 100, "The lens changed the FOV option");
                AfkCinematics.config().useGameFov = true;
                require(Math.abs(fieldOfView(client, true) - 100) < 0.001, "Game lens did not preserve the normal FOV");
                AfkCinematics.config().useGameFov = false;
                AfkCinematics.config().customFov = 30;
                require(Math.abs(fieldOfView(client, true) - 30) < 0.001, "Custom FOV did not reach the slider minimum");
                AfkCinematics.stop();
                require(Math.abs(fieldOfView(client, true) - 100) < 0.001, "Stopping retained the cinematic lens");
                require(client.options.fov().get() == 100, "Stopping changed the FOV option");
            });
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().useGameFov = true;
                AfkCinematics.config().customFov = 90;
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
