package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.Shot;
import dev.ohneb.afkcinematics.mixin.KeyMappingAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.Difficulty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ScreenshotGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        var screenshotKey = context.computeOnClient(client -> ((KeyMappingAccess)client.options.keyScreenshot).afkCinematics$key());
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                floor(server.getPlayerList().getPlayers().getFirst());
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(20);
            context.runOnClient(client -> {
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.DRIFT);
                client.options.keyScreenshot.setKey(keyboardKey(InputConstants.KEY_F2));
                KeyMapping.resetMapping();
            });
            start(context);
            context.waitTicks(140);
            context.runOnClient(client -> { checkScreenshotView = true; cinematicScreenshots = 0; });
            capture(context, InputConstants.KEY_F2, "cinematic-f2");
            Class<?> fabrishot;
            try { fabrishot = Class.forName("me.ramidzkh.fabrishot.Fabrishot"); }
            catch (ClassNotFoundException absent) { return; }
            var binding = (KeyMapping)fabrishot.getField("SCREENSHOT_BINDING").get(null);
            var config = Class.forName("me.ramidzkh.fabrishot.config.Config");
            int width = config.getField("CAPTURE_WIDTH").getInt(null);
            int height = config.getField("CAPTURE_HEIGHT").getInt(null);
            var original = ((KeyMappingAccess)binding).afkCinematics$key();
            try {
                context.runOnClient(client -> {
                    try {
                        config.getField("CAPTURE_WIDTH").setInt(null, 3840);
                        config.getField("CAPTURE_HEIGHT").setInt(null, 2160);
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                    binding.setKey(keyboardKey(InputConstants.KEY_F9));
                    KeyMapping.resetMapping();
                });
                capture(context, InputConstants.KEY_F9, "cinematic-fabrishot-4k");
                context.runOnClient(client -> {
                    binding.setKey(keyboardKey(InputConstants.KEY_W));
                    KeyMapping.resetMapping();
                });
                capture(context, InputConstants.KEY_W, "cinematic-fabrishot-rebound");
                context.runOnClient(client -> require(!client.options.keyUp.isDown() && !client.options.keyUp.consumeClick(),
                    "Rebound Fabrishot capture activated movement"));
            } finally {
                context.runOnClient(client -> {
                    try {
                        config.getField("CAPTURE_WIDTH").setInt(null, width);
                        config.getField("CAPTURE_HEIGHT").setInt(null, height);
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                    binding.setKey(original);
                    KeyMapping.resetMapping();
                });
            }
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally {
            context.runOnClient(client -> {
                checkScreenshotView = false;
                AfkCinematics.stop();
                client.options.keyScreenshot.setKey(screenshotKey);
                KeyMapping.resetMapping();
                focused = null;
            });
        }
    }

    private static void capture(ClientGameTestContext context, int key, String label) throws Exception {
        Path folder = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("screenshots"));
        Set<Path> before = images(folder);
        int reads = context.computeOnClient(client -> cinematicScreenshots);
        var player = context.computeOnClient(client -> client.player.position());
        key(context, key, InputConstants.PRESS);
        key(context, key, InputConstants.REPEAT);
        key(context, key, InputConstants.RELEASE);
        context.waitFor(client -> images(folder).stream().anyMatch(path -> !before.contains(path) && complete(path)), 240);
        context.runOnClient(client -> {
            require(AfkCinematics.showing(), "Taking a screenshot stopped the cinematic");
            require(cinematicScreenshots > reads, "Screenshot did not reach cinematic readback");
            require(client.player.position().equals(player), "Screenshot changed the player position");
        });
        Path image = images(folder).stream().filter(path -> !before.contains(path) && complete(path)).findFirst().orElseThrow();
        var artifacts = Files.createDirectories(Path.of(System.getProperty("afkcinematics.test.artifacts")));
        Files.copy(image, artifacts.resolve(label + "-" + image.getFileName()));
        var captured = context.computeOnClient(client -> camera(client).position());
        context.waitFor(client -> camera(client).position().distanceTo(captured) > 0.001, 120);
        context.runOnClient(client -> require(AfkCinematics.showing(), "Camera did not resume after capture"));
    }

    private static Set<Path> images(Path folder) {
        if (!Files.isDirectory(folder)) return Set.of();
        try (var files = Files.list(folder)) {
            return files.filter(path -> path.toString().endsWith(".png")).collect(Collectors.toSet());
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }

    private static boolean complete(Path image) {
        // The asynchronous writer creates the file before its PNG data is complete.
        try (var channel = Files.newByteChannel(image)) {
            if (channel.size() < 12) return false;
            var tail = java.nio.ByteBuffer.allocate(12);
            channel.position(channel.size() - 12);
            if (channel.read(tail) != 12) return false;
            tail.flip();
            return tail.getInt() == 0 && tail.getInt() == 0x49454e44 && tail.getInt() == 0xae426082;
        } catch (java.io.IOException failure) { return false; }
    }
}
