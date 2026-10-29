package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class NaturalSceneGameTest implements FabricClientGameTest {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NaturalSceneGameTest.class);
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        String capture = System.getProperty("afkcinematics.test.capture");
        if (capture != null && !capture.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid capture label");
        Path root = Path.of(System.getProperty("afkcinematics.test.artifacts"));
        Path artifacts = capture == null ? root : root.resolve("cinematography-" + capture);
        var captures = new ArrayList<String>();
        if (capture != null) captures.add("file,nanoseconds");
        if (capture != null) {
            try {
                Files.createDirectories(root);
                Files.createDirectory(artifacts);
            } catch (java.io.IOException exception) {
                throw new AssertionError("Capture needs a new artifact directory: " + artifacts, exception);
            }
        }
        try (AutoCloseable diagnostics = () -> saveDiagnostics(context, artifacts, captures)) {
            Files.createDirectories(artifacts);
            try (var world = context.worldBuilder().setUseConsistentSettings(false)
                    .adjustSettings(settings -> settings.setSeed("20261029")).create()) {
                world.getServer().runOnServer(server -> {
                    server.setDifficulty(Difficulty.PEACEFUL, true);
                    server.overworld().getGameRules().set(GameRules.SPAWN_MOBS, false, server);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.SURVIVAL);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32));
                    player.inventoryMenu.broadcastFullState();
                    if (capture != null) {
                        player.teleportTo(-8.5, 65, -5.5);
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 1000");
                    }
                });
                context.waitFor(client -> client.player.onGround() && !client.player.isInWater()
                    && client.player.getInventory().countItem(Items.EMERALD) == 32, 200);
                context.waitTicks(30);
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    // Generated passive mobs can push a stationary player and correctly cancel the camera.
                    server.overworld().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(128)).forEach(Mob::discard);
                });
                Pose playerPose = context.computeOnClient(client -> new Pose(WorldGeometry.point(client.player.position()),
                    client.player.getYRot(), client.player.getXRot()));
                context.runOnClient(client -> {
                    if (capture != null) {
                        try {
                            var field = AfkCinematics.class.getDeclaredField("RANDOM");
                            field.setAccessible(true);
                            ((java.util.Random)field.get(null)).setSeed(20261029);
                        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                    }
                    AfkCinematics.config().subject = CinematicConfig.Subject.SCENERY;
                    AfkCinematics.config().pace = CinematicConfig.Pace.SHORT;
                    AfkCinematics.config().hideHud = true;
                    AfkCinematics.config().letterbox = true;
                    cameraTimings = new ArrayList<>();
                    cameraStop = null;
                    cameraTimings.add("operation,nanoseconds");
                    sceneryFrames = new ArrayList<>();
                    sceneryFrames.add("nanoseconds,x,y,z,opacity,scenery");
                });
                start(context);
                context.waitFor(client -> AfkCinematics.scenery() && AfkCinematics.fade() == 0, 200);
                int duration = Math.clamp(Integer.getInteger("afkcinematics.test.naturalSeconds", 40), 40, 3600);
                long started = System.nanoTime();
                long lastReveal = started;
                boolean fading = false;
                int completedShots = 0;
                long nextReport = 60;
                for (int tick = 0; System.nanoTime() - started < duration * 1_000_000_000L; tick++) {
                    boolean uncovered = context.computeOnClient(client -> {
                        require(AfkCinematics.showing(), "Natural scenery stopped unexpectedly: " + cameraStop);
                        var point = WorldGeometry.point(camera(client).position());
                        require(Scene.inRange(point, playerPose.position()) && new WorldGeometry(client.level).clear(point, point),
                            "Natural scenery left the validated camera range");
                        require(WorldGeometry.point(client.player.position()).distance(playerPose.position()) < 0.0001
                            && client.player.getYRot() == playerPose.yaw() && client.player.getXRot() == playerPose.pitch(),
                            "Natural scenery changed the player pose");
                        return AfkCinematics.fade() == 0;
                    });
                    if (!uncovered) fading = true;
                    else if (fading) {
                        lastReveal = System.nanoTime();
                        fading = false;
                        completedShots++;
                    }
                    require(System.nanoTime() - lastReveal < 35_000_000_000L,
                        "Natural scenery did not reveal another shot within 35 seconds");
                    if (capture != null) {
                        Path frame = context.takeScreenshot(TestScreenshotOptions.of("frame").withDestinationDir(artifacts));
                        captures.add(frame.getFileName() + "," + System.nanoTime());
                    } else if (tick == 200 || tick == 450 || tick == 700) {
                        context.waitFor(client -> AfkCinematics.fade() == 0, 100);
                        context.takeScreenshot(TestScreenshotOptions.of("natural-scenery-" + tick).withDestinationDir(artifacts));
                    }
                    if (capture == null) context.waitTicks(1);
                    long elapsed = (System.nanoTime() - started) / 1_000_000_000L;
                    if (elapsed >= nextReport) {
                        LOGGER.info("Natural scenery validation: {} of {} seconds, {} completed shots", elapsed, duration, completedShots);
                        nextReport = elapsed + 60;
                    }
                }
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    require(player.position().distanceToSqr(WorldGeometry.vector(playerPose.position())) < 0.0001
                        && player.getInventory().countItem(Items.EMERALD) == 32, "Natural scenery changed server state");
                });
            }
            context.waitTicks(3);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Disconnecting during a shot retained camera ownership"));
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static void saveDiagnostics(ClientGameTestContext context, Path artifacts, List<String> captures) throws java.io.IOException {
        try {
            var timings = context.computeOnClient(client -> cameraTimings == null ? List.<String>of() : List.copyOf(cameraTimings));
            var frames = context.computeOnClient(client -> sceneryFrames == null ? List.<String>of() : List.copyOf(sceneryFrames));
            Files.write(artifacts.resolve("natural-camera-timings.csv"), timings);
            Files.write(artifacts.resolve("natural-scenery-frames.csv"), frames);
            if (!captures.isEmpty()) Files.write(artifacts.resolve("capture-frames.csv"), captures);
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                cameraTimings = null;
                sceneryFrames = null;
                focused = null;
            });
        }
    }
}
