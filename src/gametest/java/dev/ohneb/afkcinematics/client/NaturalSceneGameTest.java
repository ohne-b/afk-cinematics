package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Vec;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import com.mojang.blaze3d.platform.InputConstants;

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
        String scene = System.getProperty("afkcinematics.test.scene", "forest");
        if (capture != null && !capture.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid capture label");
        if (capture != null && (scene.equals("boat") || scene.equals("flight"))) {
            JourneyCapture.run(context, capture, scene.equals("boat"));
            return;
        }
        if (capture != null && !List.of("forest", "plains", "cavern", "fishing").contains(scene))
            throw new IllegalArgumentException("Unknown capture scene: " + scene);
        boolean fishing = capture != null && scene.equals("fishing");
        Path root = Path.of(System.getProperty("afkcinematics.test.artifacts"));
        Path artifacts = capture == null ? root : root.resolve("cinematography-" + capture);
        var captures = new ArrayList<String>();
        var capturePosition = new java.util.concurrent.atomic.AtomicReference<Vec>();
        var fishingView = new java.util.concurrent.atomic.AtomicReference<Pose>();
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
                    player.getInventory().setItem(0, new ItemStack(fishing ? Items.FISHING_ROD : Items.EMERALD, fishing ? 1 : 32));
                    player.inventoryMenu.broadcastFullState();
                    if (capture != null) {
                        player.teleportTo(-8.5, 65, -5.5);
                        if (fishing) {
                            Pose bank = fishingBank(server.overworld());
                            fishingView.set(bank);
                            player.teleportTo(bank.position().x(), bank.position().y(), bank.position().z());
                            player.setYRot((float)bank.yaw());
                            player.setXRot((float)bank.pitch());
                        } else if (scene.equals("plains")) {
                            var biome = server.overworld().findClosestBiome3d(holder -> holder.is(Biomes.PLAINS),
                                BlockPos.ZERO, 4096, 32, 64);
                            require(biome != null, "Capture seed has no nearby plains");
                            var position = dryGround(server.overworld(), biome.getFirst());
                            player.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
                        } else if (scene.equals("cavern")) {
                            floor(player);
                            SceneryGameTest.cavern(player, player.blockPosition().below());
                        }
                        LOGGER.info("Capture scene {} starts at {}", scene, player.position());
                        capturePosition.set(WorldGeometry.point(player.position()));
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 1000");
                    }
                });
                context.waitFor(client -> client.player.isAlive() && client.player.onGround() && !client.player.isInWater()
                    && (capture == null || WorldGeometry.point(client.player.position()).distance(capturePosition.get()) < 2)
                    && (fishing ? client.player.getMainHandItem().is(Items.FISHING_ROD)
                        : client.player.getInventory().countItem(Items.EMERALD) == 32), 200);
                if (capture != null) context.waitFor(client -> {
                    int x = client.player.blockPosition().getX() >> 4, z = client.player.blockPosition().getZ() >> 4;
                    for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3})
                        if (!client.level.getChunkSource().hasChunk(x + dx, z + dz)) return false;
                    return true;
                }, 400);
                context.waitTicks(30);
                if (fishing) context.runOnClient(client -> {
                    client.player.setYRot((float)fishingView.get().yaw());
                    client.player.setXRot((float)fishingView.get().pitch());
                });
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
                    AfkCinematics.config().fishing = true;
                    String selectedShots = System.getProperty("afkcinematics.test.shots");
                    if (selectedShots != null) {
                        var choices = java.util.EnumSet.noneOf(Shot.Type.class);
                        for (String choice : selectedShots.split(",")) choices.add(Shot.Type.valueOf(choice));
                        AfkCinematics.config().shots = choices;
                    }
                    AfkCinematics.config().pace = CinematicConfig.Pace.SHORT;
                    AfkCinematics.config().hideHud = true;
                    AfkCinematics.config().letterbox = true;
                    cameraTimings = new ArrayList<>();
                    recordingFrames = capture != null;
                    cameraStop = null;
                    revealedShot = null;
                    cameraTimings.add("operation,nanoseconds");
                    sceneryFrames = new ArrayList<>();
                    sceneryFrames.add("nanoseconds,x,y,z,opacity,scenery,shot,type,yaw,pitch");
                });
                if (fishing) {
                    context.runOnClient(client -> AfkCinematics.config().automatic = true);
                    mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
                    context.waitTicks(1);
                    mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
                    context.waitTicks(20);
                    context.runOnClient(client -> LOGGER.info("Fishing capture hook: {}, usable: {}",
                        client.player.fishing == null ? "none" : client.player.fishing.position(),
                        FishingActivity.hook(client.player, client.level) != null));
                    context.waitFor(client -> FishingActivity.hook(client.player, client.level) != null, 60);
                } else start(context);
                context.waitFor(client -> AfkCinematics.showing() && (fishing || AfkCinematics.scenery()) && AfkCinematics.fade() == 0, 200);
                int duration = Math.clamp(Integer.getInteger("afkcinematics.test.naturalSeconds", 40), 40, 3600);
                long started = System.nanoTime();
                long lastReveal = started;
                Shot previousShot = context.computeOnClient(client -> revealedShot);
                int revealedShots = 0;
                long nextReport = 60;
                for (int tick = 0; System.nanoTime() - started < duration * 1_000_000_000L; tick++) {
                    Shot latestShot = context.computeOnClient(client -> {
                        require(AfkCinematics.showing(), "Natural scenery stopped unexpectedly: " + cameraStop);
                        var point = WorldGeometry.point(camera(client).position());
                        require(Scene.inRange(point, playerPose.position()) && new WorldGeometry(client.level, AfkCinematics.underwater()).clear(point, point),
                            "Natural scenery left the validated camera range");
                        require(WorldGeometry.point(client.player.position()).distance(playerPose.position()) < 0.0001
                            && client.player.getYRot() == playerPose.yaw() && client.player.getXRot() == playerPose.pitch(),
                            "Natural scenery changed the player pose");
                        return revealedShot;
                    });
                    if (latestShot != null && latestShot != previousShot) {
                        lastReveal = System.nanoTime();
                        previousShot = latestShot;
                        revealedShots++;
                    }
                    require(System.nanoTime() - lastReveal < 35_000_000_000L,
                        "Natural scenery did not reveal another take within 35 seconds");
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
                        LOGGER.info("Natural scenery validation: {} of {} seconds, {} new takes revealed", elapsed, duration, revealedShots);
                        nextReport = elapsed + 60;
                    }
                }
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    require(player.position().distanceToSqr(WorldGeometry.vector(playerPose.position())) < 0.0001
                        && (fishing ? player.fishing != null && player.getMainHandItem().is(Items.FISHING_ROD)
                            : player.getInventory().countItem(Items.EMERALD) == 32), "Natural scenery changed server state");
                });
            }
            context.waitTicks(3);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Disconnecting during a shot retained camera ownership"));
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static Pose fishingBank(ServerLevel level) {
        var beach = level.findClosestBiome3d(holder -> holder.is(Biomes.BEACH), BlockPos.ZERO, 4096, 32, 64);
        require(beach != null, "Capture seed has no nearby beach");
        BlockPos center = beach.getFirst();
        for (int radius = 0; radius <= 64; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = center.getX() + dx, z = center.getZ() + dz;
                level.getChunkAt(new BlockPos(x, 64, z));
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos floor = new BlockPos(x, y - 1, z);
                if (!level.getBlockState(floor).getFluidState().isEmpty() || !level.getBlockState(floor.above()).isAir()
                    || !level.getBlockState(floor.above(2)).isAir()) continue;
                for (int direction = 0; direction < 8; direction++) {
                    double angle = direction * Math.PI / 4;
                    boolean open = true;
                    for (int distance = 3; distance <= 18; distance += 3) {
                        int waterX = (int)Math.floor(x + .5 + Math.sin(angle) * distance);
                        int waterZ = (int)Math.floor(z + .5 + Math.cos(angle) * distance);
                        level.getChunkAt(new BlockPos(waterX, 64, waterZ));
                        int waterY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, waterX, waterZ);
                        if (y < waterY || y > waterY + 2
                            || !level.getFluidState(new BlockPos(waterX, waterY - 1, waterZ)).is(FluidTags.WATER)) { open = false; break; }
                    }
                    if (open) return new Pose(new Vec(x + .5, y, z + .5), -Math.toDegrees(angle), 20);
                }
            }
        }
        throw new AssertionError("Capture beach has no clear fishing bank");
    }

    private static BlockPos dryGround(ServerLevel level, BlockPos center) {
        for (int radius = 0; radius <= 32; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) for (int dz = -radius; dz <= radius; dz += 4) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos column = center.offset(dx, 0, dz);
                level.getChunkAt(column);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                BlockPos ground = new BlockPos(column.getX(), y - 1, column.getZ());
                var state = level.getBlockState(ground);
                if (!state.isAir() && state.getFluidState().isEmpty()) return ground.above();
            }
        }
        throw new AssertionError("Plains capture has no nearby dry ground");
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
                AfkCinematics.config().automatic = false;
                cameraTimings = null;
                recordingFrames = false;
                sceneryFrames = null;
                revealedShot = null;
                focused = null;
            });
        }
    }
}
