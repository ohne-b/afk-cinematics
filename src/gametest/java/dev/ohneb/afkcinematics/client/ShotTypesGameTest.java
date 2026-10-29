package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ShotTypesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        Path artifacts = Path.of(System.getProperty("afkcinematics.test.artifacts"));
        var samples = new ArrayList<String>();
        samples.add("type,start_x,start_y,start_z,end_x,end_y,end_z,start_yaw,end_yaw");
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                floor(player);
                BlockPos floor = player.blockPosition().below();
                for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-18, 0, -18), floor.offset(18, 14, 18)))
                    player.level().setBlockAndUpdate(pos, (pos.getY() == floor.getY() ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState());
                for (BlockPos pos : BlockPos.betweenClosed(floor.offset(7, 4, 6), floor.offset(11, 6, 10)))
                    player.level().setBlockAndUpdate(pos, Blocks.OAK_LEAVES.defaultBlockState());
                for (int y = 1; y <= 5; y++) player.level().setBlockAndUpdate(floor.offset(9, y, 8), Blocks.OAK_LOG.defaultBlockState());
                for (int x = -5; x <= 5; x += 2) player.level().setBlockAndUpdate(floor.offset(x, 1, 7), Blocks.POPPY.defaultBlockState());
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(30);
            var playerPose = context.computeOnClient(client -> new Pose(WorldGeometry.point(client.player.position()),
                client.player.getYRot(), client.player.getXRot()));
            for (Shot.Type type : Shot.Type.values()) {
                context.runOnClient(client -> {
                    AfkCinematics.config().subject = type == Shot.Type.OVERHEAD ? CinematicConfig.Subject.SCENERY : CinematicConfig.Subject.PLAYER;
                    AfkCinematics.config().shots = EnumSet.of(type);
                    AfkCinematics.config().pace = CinematicConfig.Pace.SHORT;
                    AfkCinematics.config().letterbox = true;
                    cameraTimings = new ArrayList<>();
                    cameraStop = null;
                });
                start(context);
                context.waitFor(client -> settled(), 200);
                context.waitTicks(2);
                Pose first = pose(context);
                for (int tick = 0; tick < 40; tick++) {
                    context.runOnClient(client -> {
                        require(AfkCinematics.showing(), "Selected shot stopped: " + type + "\n" + cameraStop);
                        var position = WorldGeometry.point(camera(client).position());
                        require(new WorldGeometry(client.level).clear(position, position), "Selected shot clipped terrain: " + type);
                        require(WorldGeometry.point(client.player.position()).distance(playerPose.position()) < 0.0001
                            && client.player.getYRot() == playerPose.yaw() && client.player.getXRot() == playerPose.pitch(),
                            "Selected shot changed the player pose: " + type);
                    });
                    context.waitTicks(1);
                }
                Pose last = pose(context);
                if (type == Shot.Type.STILL) require(first.position().distance(last.position()) < 0.00001
                    && Math.abs(Math.IEEEremainder(first.yaw() - last.yaw(), 360)) < 0.00001,
                    "Still shot moved after entry: " + first + " -> " + last);
                else if (type == Shot.Type.PAN) require(first.position().distance(last.position()) < 0.00001
                    && Math.abs(Math.IEEEremainder(first.yaw() - last.yaw(), 360)) > 0.01, "Panorama did not rotate in place");
                else require(first.position().distance(last.position()) > 0.001, "Selected moving shot stayed still: " + type);
                context.takeScreenshot(TestScreenshotOptions.of("shot-" + type.name().toLowerCase(Locale.ROOT)).withDestinationDir(artifacts));
                samples.add(type + "," + first.position().x() + "," + first.position().y() + "," + first.position().z()
                    + "," + last.position().x() + "," + last.position().y() + "," + last.position().z() + "," + first.yaw() + "," + last.yaw());
                context.runOnClient(client -> AfkCinematics.stop());
            }
            context.runOnClient(client -> {
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.STILL);
                client.player.setXRot(90);
            });
            start(context);
            for (int tick = 0; tick < 110; tick++) {
                context.runOnClient(client -> {
                    require(AfkCinematics.showing(), "Wide entry aborted while looking down at flat ground");
                    require(client.player.getXRot() == 90, "Wide entry rotated the player");
                });
                context.waitTicks(1);
            }
            context.waitFor(client -> settled(), 200);
            context.takeScreenshot(TestScreenshotOptions.of("wide-downward-entry").withDestinationDir(artifacts));
            context.runOnClient(client -> {
                AfkCinematics.stop();
                client.player.setXRot((float)playerPose.pitch());
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.PAN);
            });
            start(context);
            context.waitTicks(440);
            double travel = 0;
            double previousYaw = pose(context).yaw();
            for (int tick = 0; tick < 180; tick++) {
                context.runOnClient(client -> require(AfkCinematics.showing(), "Repeated panorama stopped"));
                context.waitTicks(1);
                double yaw = pose(context).yaw();
                travel += Math.abs(Math.IEEEremainder(yaw - previousYaw, 360));
                previousYaw = yaw;
            }
            require(travel > 0.5, "Repeated panorama became a still shot");
            samples.add("repeated_pan_degrees," + travel);
            try { Files.write(artifacts.resolve("shot-samples.csv"), samples); }
            catch (Exception exception) { throw new AssertionError(exception); }
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().shots = EnumSet.allOf(Shot.Type.class);
                AfkCinematics.config().pace = CinematicConfig.Pace.RELAXED;
                cameraTimings = null;
                focused = null;
            });
        }
    }

    private static Pose pose(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            var camera = camera(client);
            return new Pose(WorldGeometry.point(camera.position()), camera.yRot(), camera.xRot());
        });
    }

    private static boolean settled() {
        try {
            var field = AfkCinematics.class.getDeclaredField("session");
            field.setAccessible(true);
            var session = (dev.ohneb.afkcinematics.core.CinematicSession)field.get(null);
            return session != null && session.settled();
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
