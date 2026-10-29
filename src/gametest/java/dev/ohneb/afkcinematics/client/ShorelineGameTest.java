package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ShorelineGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        var origin = new AtomicReference<BlockPos>();
        var artifacts = Path.of(System.getProperty("afkcinematics.test.artifacts"));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                floor(player);
                BlockPos base = player.blockPosition().below();
                origin.set(base.above(8));
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-26, 0, -26), base.offset(26, 18, 26))) {
                    int y = block.getY() - base.getY();
                    boolean pool = block.getX() > base.getX() + 2 && block.getX() < base.getX() + 26
                        && Math.abs(block.getZ() - base.getZ()) < 26;
                    var material = y > 8 ? Blocks.AIR : pool && y > 2 ? Blocks.WATER
                        : y == (pool ? 2 : 8) ? pool ? Blocks.SAND : Blocks.GRASS_BLOCK : Blocks.STONE;
                    player.level().setBlock(block, material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                player.teleportTo(base.getX() + 0.5, base.getY() + 9, base.getZ() + 0.5);
            });
            context.waitFor(client -> client.player.onGround() && client.player.blockPosition().below().equals(origin.get()));
            context.waitTicks(30);
            Pose initial = context.computeOnClient(client -> new Pose(WorldGeometry.point(client.player.position()),
                client.player.getYRot(), client.player.getXRot()));
            context.runOnClient(client -> {
                var geometry = new WorldGeometry(client.level);
                Pose entry = new Pose(WorldGeometry.point(client.player.getEyePosition()), initial.yaw(), initial.pitch());
                boolean ground = false, water = false;
                for (int probe = 0; probe < Scene.PROBES; probe++) {
                    Scene scene = geometry.scenery(initial.position(), entry, initial.yaw(), probe);
                    if (scene == null) continue;
                    ground |= scene.kind() == Scene.Kind.GROUND;
                    water |= scene.kind() == Scene.Kind.WATER;
                }
                require(ground && water, "Shoreline search did not find both ground and submerged views: " + ground + "/" + water);
                AfkCinematics.config().subject = CinematicConfig.Subject.SCENERY;
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.DRIFT, Shot.Type.FLYOVER, Shot.Type.LOW_PASS);
                AfkCinematics.config().letterbox = true;
                try {
                    var random = AfkCinematics.class.getDeclaredField("RANDOM");
                    random.setAccessible(true);
                    ((Random)random.get(null)).setSeed(19);
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            });
            start(context);
            context.waitFor(client -> AfkCinematics.scenery() && AfkCinematics.fade() == 0, 160);
            for (int view = 0; view < 8 && !context.computeOnClient(client -> AfkCinematics.underwater()); view++) next(context);
            context.runOnClient(client -> {
                require(AfkCinematics.underwater(), "Submerged views never entered the shoreline sequence");
                require(camera(client).getFluidInCamera() == FogType.WATER, "Rendered camera did not use water fog");
                require(!client.player.isInWater(), "Underwater camera changed the player environment");
            });
            context.takeScreenshot(TestScreenshotOptions.of("shoreline-underwater").withDestinationDir(artifacts));
            for (int tick = 0; tick < 60; tick++) {
                context.runOnClient(client -> {
                    var position = WorldGeometry.point(camera(client).position());
                    require(AfkCinematics.showing() && AfkCinematics.underwater(), "Submerged take stopped early");
                    require(new WorldGeometry(client.level, true).clear(position, position), "Underwater drive left its water volume");
                    require(WorldGeometry.point(client.player.position()).distance(initial.position()) < 0.0001
                        && client.player.getYRot() == initial.yaw() && client.player.getXRot() == initial.pitch(),
                        "Underwater take changed the player pose");
                });
                context.waitTicks(1);
            }
            for (int view = 0; view < 8 && context.computeOnClient(client -> AfkCinematics.underwater()); view++) next(context);
            context.runOnClient(client -> {
                require(!AfkCinematics.underwater(), "Shoreline sequence never returned to a dry view");
                require(camera(client).getFluidInCamera() != FogType.WATER, "Dry destination retained the water environment");
            });
            context.takeScreenshot(TestScreenshotOptions.of("shoreline-dry-view").withDestinationDir(artifacts));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.position().distanceToSqr(WorldGeometry.vector(initial.position())) < 0.0001,
                    "Underwater take moved the server player");
            });
        } finally {
            context.runOnClient(client -> { AfkCinematics.stop(); focused = null; });
        }
    }

    private static void next(ClientGameTestContext context) {
        context.runOnClient(client -> AfkCinematics.nextShot());
        context.waitFor(client -> AfkCinematics.fade() > 0, 200);
        context.waitFor(client -> AfkCinematics.showing() && AfkCinematics.fade() == 0, 100);
    }
}
