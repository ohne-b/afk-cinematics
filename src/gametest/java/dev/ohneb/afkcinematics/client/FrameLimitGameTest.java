package dev.ohneb.afkcinematics.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.Difficulty;
import java.util.ArrayList;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class FrameLimitGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        int previous = context.computeOnClient(client -> client.options.framerateLimit().get());
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                floor(server.getPlayerList().getPlayers().getFirst());
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(20);
            context.runOnClient(client -> {
                client.options.framerateLimit().set(20);
                require(client.getFramerateLimitTracker().getFramerateLimit() == 20, "Normal cap setup failed");
                AfkCinematics.config().cinematicFps = 60;
            });
            start(context);
            context.runOnClient(client -> {
                require(client.getFramerateLimitTracker().getFramerateLimit() == 60, "Cinematic cap did not replace the lower normal cap");
                AfkCinematics.config().cinematicFps = 144;
                require(client.getFramerateLimitTracker().getFramerateLimit() == 144, "Cinematic cap could not reach 144");
                require(client.options.framerateLimit().get() == 20, "Cinematic cap changed the normal option");
                checkDynamicFps(144);
                focused = false;
                cameraTimings = new ArrayList<>();
            });
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Background cinematic stopped with background mode enabled");
                require(client.getFramerateLimitTracker().getFramerateLimit() == 144, "Background throttle replaced the cinematic cap");
                require(cameraTimings.size() > 5, "The cap changed without rendering cinematic frames");
                AfkCinematics.config().unfocused = false;
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                require(!AfkCinematics.showing(), "Background-disabled cinematic stayed active");
                require(AfkCinematics.frameLimit() == 0, "Stopped cinematic retained its cap");
                focused = true;
                AfkCinematics.config().unfocused = true;
            });
            start(context);
            context.runOnClient(client -> {
                AfkCinematics.showScreen(new SettingsScreen(null));
                require(AfkCinematics.frameLimit() == 0, "A settings screen retained the cinematic cap");
                require(client.options.framerateLimit().get() == 20, "Settings changed the normal cap");
                AfkCinematics.showScreen(null);
                require(client.getFramerateLimitTracker().getFramerateLimit() == 20, "Normal cap did not resume on exit");
            });
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                client.options.framerateLimit().set(previous);
                AfkCinematics.config().cinematicFps = 60;
                cameraTimings = null;
                focused = null;
            });
        }
    }

    private static void checkDynamicFps(int expected) {
        try {
            var method = Class.forName("dynamic_fps.impl.DynamicFPSMod").getDeclaredMethod("targetFrameRate");
            method.setAccessible(true);
            require((int)method.invoke(null) == expected, "Dynamic FPS still throttled its own renderer");
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
