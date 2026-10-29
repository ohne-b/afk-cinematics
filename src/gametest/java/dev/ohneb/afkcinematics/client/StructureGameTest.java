package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.CinematicSession;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

import java.nio.file.Path;
import java.util.Random;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class StructureGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        var artifacts = Path.of(System.getProperty("afkcinematics.test.artifacts"));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                floor(player);
                BlockPos base = player.blockPosition().below();
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-26, 0, -26), base.offset(26, 18, 26))) {
                    player.level().setBlock(block, (block.getY() == base.getY() ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                for (int x = -4; x <= 4; x++) for (int z = 8; z <= 16; z++) for (int y = 0; y <= 4; y++) {
                    boolean wall = Math.abs(x) == 4 || z == 8 || z == 16;
                    var material = y == 0 || wall ? Blocks.OAK_PLANKS : Blocks.AIR;
                    if (z == 8 && Math.abs(x) == 2 && (y == 2 || y == 3)) material = Blocks.GLASS;
                    if (z == 8 && x == 0 && (y == 1 || y == 2)) material = Blocks.AIR;
                    player.level().setBlockAndUpdate(base.offset(x, y, z), material.defaultBlockState());
                }
                for (int x = -5; x <= 5; x++) for (int z = 7; z <= 17; z++)
                    player.level().setBlockAndUpdate(base.offset(x, 5 + (5 - Math.abs(x)) / 2, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                for (int x = -6; x <= 6; x += 2) player.level().setBlockAndUpdate(base.offset(x, 1, 5), Blocks.POPPY.defaultBlockState());
                for (int y = 1; y <= 5; y++) player.level().setBlockAndUpdate(base.offset(-12, y, 3), Blocks.OAK_LOG.defaultBlockState());
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-14, 4, 1), base.offset(-10, 6, 5)))
                    player.level().setBlockAndUpdate(block, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(30);
            Pose initial = context.computeOnClient(client -> new Pose(WorldGeometry.point(client.player.position()),
                client.player.getYRot(), client.player.getXRot()));
            context.runOnClient(client -> {
                var geometry = new WorldGeometry(client.level);
                Pose entry = new Pose(WorldGeometry.point(client.player.getEyePosition()), initial.yaw(), initial.pitch());
                int structures = 0;
                for (int probe = 0; probe < Scene.PROBES; probe++) {
                    var scene = geometry.scenery(initial.position(), entry, initial.yaw(), probe);
                    if (scene != null && scene.kind() == Scene.Kind.STRUCTURE) {
                        structures++;
                        require(geometry.visible(scene.vantage(), scene.focus()), "Building subject was not visible");
                    }
                }
                require(structures > 0, "Nearby cottage was not found");
                AfkCinematics.config().subject = CinematicConfig.Subject.SCENERY;
                AfkCinematics.config().letterbox = true;
                try {
                    var random = AfkCinematics.class.getDeclaredField("RANDOM");
                    random.setAccessible(true);
                    ((Random)random.get(null)).setSeed(52);
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
            });
            start(context);
            context.waitFor(client -> AfkCinematics.scenery() && AfkCinematics.fade() == 0, 160);
            for (int view = 0; view < 6 && context.computeOnClient(client -> kind()) != Scene.Kind.STRUCTURE; view++) next(context);
            context.runOnClient(client -> require(kind() == Scene.Kind.STRUCTURE, "Cottage never entered the scenery sequence"));
            context.takeScreenshot(TestScreenshotOptions.of("structure-cottage").withDestinationDir(artifacts));
            context.waitTicks(60);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Building take stopped early");
                var position = WorldGeometry.point(camera(client).position());
                require(new WorldGeometry(client.level).clear(position, position), "Building take clipped terrain");
                require(WorldGeometry.point(client.player.position()).distance(initial.position()) < 0.0001
                    && client.player.getYRot() == initial.yaw() && client.player.getXRot() == initial.pitch(), "Building take changed the player pose");
            });
            for (int view = 0; view < 3 && context.computeOnClient(client -> kind()) == Scene.Kind.STRUCTURE; view++) next(context);
            context.runOnClient(client -> require(kind() != Scene.Kind.STRUCTURE, "Building dominated the sequence despite open surroundings"));
            context.takeScreenshot(TestScreenshotOptions.of("structure-surroundings").withDestinationDir(artifacts));
        } finally {
            context.runOnClient(client -> { AfkCinematics.stop(); focused = null; });
        }
    }

    private static void next(ClientGameTestContext context) {
        context.runOnClient(client -> AfkCinematics.nextShot());
        context.waitFor(client -> AfkCinematics.fade() > 0, 200);
        context.waitFor(client -> AfkCinematics.showing() && AfkCinematics.fade() == 0, 100);
    }

    private static Scene.Kind kind() {
        try {
            var owner = AfkCinematics.class.getDeclaredField("session");
            owner.setAccessible(true);
            var scene = CinematicSession.class.getDeclaredField("scene");
            scene.setAccessible(true);
            return ((Scene)scene.get(owner.get(null))).kind();
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
