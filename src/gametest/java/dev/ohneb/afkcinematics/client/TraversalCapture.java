package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

/** Opt-in walking footage; real movement and jumps over a repeatable hill. */
@SuppressWarnings("UnstableApiUsage")
final class TraversalCapture {
    static void run(ClientGameTestContext context, String label, boolean steep) {
        var directory = Path.of(System.getProperty("afkcinematics.test.artifacts"), "cinematography-" + label);
        var rows = new ArrayList<String>();
        rows.add("nanoseconds,x,y,z,yaw,pitch,opacity,playerX,playerY,playerZ,grounded,type");
        var frames = new ArrayList<String>();
        var timings = new ArrayList<String>();
        timings.add("operation,nanoseconds");
        frames.add("file,nanoseconds");
        var base = new AtomicReference<BlockPos>();
        int distance = context.computeOnClient(client -> client.options.renderDistance().get());
        try {
            Files.createDirectory(directory);
            context.runOnClient(client -> client.options.renderDistance().set(10));
            try (var world = context.worldBuilder().create()) {
                world.getServer().runOnServer(server -> {
                    server.setDifficulty(Difficulty.PEACEFUL, true);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.SURVIVAL);
                    var origin = player.blockPosition().below();
                    base.set(origin);
                    for (int z = -16; z <= 140; z++) {
                        int height = z < 20 ? 0 : z < 44 ? Math.min(12, (z - 20) / (steep ? 1 : 2)) : z < 64 ? 12 : z < 88 ? (88 - z) / 2 : 0;
                        for (int x = -14; x <= 14; x++) for (int y = -3; y <= height + 10; y++) {
                            var material = y == height ? Blocks.GRASS_BLOCK : y < height ? Blocks.DIRT : Blocks.AIR;
                            player.level().setBlock(origin.offset(x, y, z), material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        }
                    }
                    player.teleportTo(origin.getX() + .5, origin.getY() + 1, origin.getZ() + .5);
                    player.setYRot(0);
                    player.setXRot(0);
                });
                context.waitFor(client -> client.player.onGround() && client.level.getBlockState(base.get().offset(0, 12, 50)).is(Blocks.GRASS_BLOCK));
                context.waitTicks(20);
                context.runOnClient(client -> {
                    client.player.setYRot(0);
                    client.player.setXRot(0);
                    var config = AfkCinematics.config();
                    config.shots = EnumSet.of(Shot.Type.TRAIL);
                    config.frontTravel = false;
                    config.pace = CinematicConfig.Pace.LONG;
                    cameraTimings = timings;
                    sceneryFrames = new ArrayList<>();
                    recordingFrames = true;
                    cameraStop = null;
                    revealedShot = null;
                });
                manual(context);
                context.waitFor(client -> AfkCinematics.fade() == 0, 100);
                key(context, InputConstants.KEY_W, InputConstants.PRESS);
                boolean jumping = false;
                long started = System.nanoTime();
                while (System.nanoTime() - started < 40_000_000_000L) {
                    double seconds = (System.nanoTime() - started) / 1e9;
                    double travelled = context.computeOnClient(client -> client.player.getZ() - base.get().getZ());
                    boolean jump = seconds > 3 && seconds < 7 || travelled > 17 && travelled < 92;
                    if (jump != jumping) {
                        jumping = jump;
                        key(context, InputConstants.KEY_SPACE, jump ? InputConstants.PRESS : InputConstants.RELEASE);
                    }
                    context.runOnClient(client -> {
                        require(AfkCinematics.showing(), "Walking view stopped: " + cameraStop);
                        if (travelled > 108) client.player.setYRot((float)Math.min(60, (travelled - 108) * 4));
                        var camera = camera(client);
                        var point = camera.position();
                        var subject = client.player.position();
                        rows.add(System.nanoTime() + "," + point.x + "," + point.y + "," + point.z
                            + "," + camera.yRot() + "," + camera.xRot() + "," + AfkCinematics.fade()
                            + "," + subject.x + "," + subject.y + "," + subject.z + "," + client.player.onGround()
                            + "," + (revealedShot == null ? "" : revealedShot.type()));
                    });
                    Path frame = context.takeScreenshot(TestScreenshotOptions.of("frame").withDestinationDir(directory));
                    frames.add(frame.getFileName() + "," + System.nanoTime());
                }
            }
        } catch (Exception error) { throw new AssertionError(error); }
        finally {
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            key(context, InputConstants.KEY_SPACE, InputConstants.RELEASE);
            context.runOnClient(client -> {
                AfkCinematics.stop();
                client.options.renderDistance().set(distance);
                cameraTimings = null;
                sceneryFrames = null;
                recordingFrames = false;
                focused = null;
            });
            try {
                Files.write(directory.resolve("journey-camera.csv"), rows);
                Files.write(directory.resolve("capture-frames.csv"), frames);
                Files.write(directory.resolve("camera-cost.csv"), timings);
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        }
    }
}
