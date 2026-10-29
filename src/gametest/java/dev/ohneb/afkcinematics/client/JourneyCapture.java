package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=26.2 {
/*import net.minecraft.world.entity.EntityTypes;
*///?} else {
import net.minecraft.world.entity.EntityType;
//?}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

/** Opt-in footage in generated terrain; adds no time to the normal suite. */
@SuppressWarnings("UnstableApiUsage")
final class JourneyCapture {
    static void run(ClientGameTestContext context, String label, boolean boat) {
        Path directory = Path.of(System.getProperty("afkcinematics.test.artifacts"), "cinematography-" + label);
        var rows = new ArrayList<String>();
        rows.add("nanoseconds,x,y,z,yaw,pitch,opacity,playerX,playerY,playerZ,type");
        var frames = new ArrayList<String>();
        frames.add("file,nanoseconds");
        var origin = new AtomicReference<Vec3>();
        int viewDistance = context.computeOnClient(client -> client.options.renderDistance().get());
        try {
            Files.createDirectories(directory.getParent());
            Files.createDirectory(directory);
            context.runOnClient(client -> client.options.renderDistance().set(12));
            try (AutoCloseable diagnostics = () -> save(context, directory, rows, frames, viewDistance);
                 var world = context.worldBuilder().setUseConsistentSettings(false)
                    .adjustSettings(settings -> settings.setSeed("20261029")).create()) {
                world.getServer().runOnServer(server -> {
                    server.setDifficulty(Difficulty.PEACEFUL, true);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    var level = server.overworld();
                    player.setGameMode(boat ? GameType.CREATIVE : GameType.SURVIVAL);
                    BlockPos start = new BlockPos(-8, 96, -5);
                    if (boat) {
                        var ocean = level.findClosestBiome3d(holder -> holder.is(Biomes.DEEP_OCEAN), BlockPos.ZERO, 4096, 32, 64);
                        require(ocean != null, "Journey seed has no nearby ocean");
                        start = new BlockPos(ocean.getFirst().getX(), level.getSeaLevel(), ocean.getFirst().getZ());
                    }
                    int highest = 0;
                    for (int x = -96; x <= 32; x += 32) for (int z = -32; z <= 320; z += 32) {
                        BlockPos point = start.offset(x, 0, z);
                        level.getChunkAt(point);
                        highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, point.getX(), point.getZ()));
                    }
                    if (!boat) start = new BlockPos(start.getX(), highest + 50, start.getZ());
                    player.teleportTo(start.getX() + .5, start.getY() + .5, start.getZ() + .5);
                    player.setYRot(0);
                    player.setXRot(0);
                    if (boat) {
                        //? if >=26.2 {
                        /*var vehicle = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
                        *///?} else {
                        var vehicle = EntityType.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
                        //?}
                        require(vehicle != null, "Missing journey boat");
                        vehicle.snapTo(player.position());
                        level.addFreshEntity(vehicle);
                        require(player.startRiding(vehicle), "Could not board journey boat");
                    } else {
                        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                        player.inventoryMenu.broadcastFullState();
                        player.setOnGround(false);
                        player.startFallFlying();
                    }
                    origin.set(player.position());
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 1000");
                });
                context.waitFor(client -> client.player.position().distanceToSqr(origin.get()) < 9
                    && (boat ? client.player.isPassenger() : client.player.isFallFlying()), 400);
                context.waitTicks(60);
                context.runOnClient(client -> {
                    var config = AfkCinematics.config();
                    config.boats = config.flight = config.travelControls = true;
                    config.cinematicFps = 60;
                    config.letterbox = true;
                    config.pace = CinematicConfig.Pace.SHORT;
                    config.shots = EnumSet.of(Shot.Type.LOW_ESCORT, Shot.Type.HIGH_TRAIL, Shot.Type.BROADSIDE);
                    String choices = System.getProperty("afkcinematics.test.shots");
                    if (choices != null) {
                        config.shots.clear();
                        for (String choice : choices.split(",")) config.shots.add(Shot.Type.valueOf(choice));
                    }
                    client.player.setYRot(0);
                    client.player.setXRot(boat ? 0 : -7);
                    cameraTimings = new ArrayList<>();
                    sceneryFrames = new ArrayList<>();
                    recordingFrames = true;
                    revealedShot = null;
                    cameraStop = null;
                });
                key(context, InputConstants.KEY_W, InputConstants.PRESS);
                start(context);
                context.waitFor(client -> AfkCinematics.fade() == 0, 100);
                org.slf4j.LoggerFactory.getLogger("JourneyCapture").info("Journey capture started: {}", label);
                long started = System.nanoTime();
                boolean left = false;
                for (int tick = 0; System.nanoTime() - started < 40_000_000_000L; tick++) {
                    double seconds = (System.nanoTime() - started) / 1e9;
                    if (boat && (seconds >= 8 && seconds < 9) != left) {
                        left = !left;
                        key(context, InputConstants.KEY_A, left ? InputConstants.PRESS : InputConstants.RELEASE);
                    }
                    context.runOnClient(client -> {
                        require(AfkCinematics.showing(), "Journey view stopped: " + cameraStop);
                        if (!boat) client.player.setYRot((float)(seconds < 8 ? 0 : seconds < 20 ? 25 * Math.sin((seconds - 8) / 12 * Math.PI / 2) : 165));
                        var camera = camera(client);
                        var point = WorldGeometry.point(camera.position());
                        var subject = WorldGeometry.point(client.player.position());
                        require(Scene.inRange(point, subject), "Journey view left its player range");
                        rows.add(System.nanoTime() + "," + point.x() + "," + point.y() + "," + point.z()
                            + "," + camera.yRot() + "," + camera.xRot() + "," + AfkCinematics.fade()
                            + "," + subject.x() + "," + subject.y() + "," + subject.z()
                            + "," + (revealedShot == null ? "" : revealedShot.type()));
                    });
                    if (!Boolean.getBoolean("afkcinematics.test.windowCapture")) {
                        Path frame = context.takeScreenshot(TestScreenshotOptions.of("frame").withDestinationDir(directory));
                        frames.add(frame.getFileName() + "," + System.nanoTime());
                    } else context.waitTicks(1);
                }
                key(context, InputConstants.KEY_W, InputConstants.RELEASE);
                world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst()
                    .position().distanceTo(origin.get()) > 20, "Journey did not move through the world"));
            }
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static void save(ClientGameTestContext context, Path directory, java.util.List<String> rows,
                             java.util.List<String> frames, int viewDistance) throws java.io.IOException {
        try {
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            key(context, InputConstants.KEY_A, InputConstants.RELEASE);
            Files.write(directory.resolve("journey-camera.csv"), rows);
            Files.write(directory.resolve("capture-frames.csv"), frames);
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                client.options.renderDistance().set(viewDistance);
                cameraTimings = null;
                sceneryFrames = null;
                recordingFrames = false;
                revealedShot = null;
                focused = null;
            });
        }
    }
}
