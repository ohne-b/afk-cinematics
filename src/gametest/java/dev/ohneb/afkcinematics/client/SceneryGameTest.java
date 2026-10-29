package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class SceneryGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        var artifacts = Path.of(System.getProperty("afkcinematics.test.artifacts"));
        var origin = new AtomicReference<BlockPos>();
        boolean hidden = context.computeOnClient(TestSupport::hidden);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                floor(player);
                origin.set(player.blockPosition().below());
                surface(player, origin.get());
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32));
                player.inventoryMenu.broadcastFullState();
                noDrops(player, "Surface fixture");
            });
            context.waitFor(client -> client.player.onGround() && client.level.getBlockState(origin.get().above(10)).is(Blocks.GRASS_BLOCK));
            context.waitTicks(30);
            var initial = context.computeOnClient(client -> new Pose(WorldGeometry.point(client.player.position()),
                client.player.getYRot(), client.player.getXRot()));
            context.runOnClient(client -> {
                AfkCinematics.config().subject = CinematicConfig.Subject.MIXED;
                AfkCinematics.config().pace = CinematicConfig.Pace.SHORT;
                AfkCinematics.config().hideHud = false;
                hide(client, true);
            });
            for (boolean destination : new boolean[]{false, true}) {
                context.runOnClient(client -> holdCoveredScenery = destination);
                start(context);
                context.waitFor(client -> AfkCinematics.fade() == 1 && AfkCinematics.scenery() == destination, 160);
                var capture = context.takeScreenshot(TestScreenshotOptions.of(destination ? "scenery-covered-destination" : "scenery-covered-origin")
                    .withDestinationDir(artifacts));
                assertBlack(capture);
                context.runOnClient(client -> { AfkCinematics.stop(); holdCoveredScenery = null; });
            }
            context.runOnClient(client -> {
                hide(client, false);
                AfkCinematics.config().hideHud = true;
                AfkCinematics.config().letterbox = true;
                sceneryFrames = new ArrayList<>();
                sceneryFrames.add("nanoseconds,x,y,z,opacity,scenery");
            });
            start(context);
            context.waitFor(client -> AfkCinematics.scenery() && AfkCinematics.fade() == 0, 160);
            checkScene(context, initial, origin.get(), true);
            context.takeScreenshot(TestScreenshotOptions.of("scenery-from-small-cave").withDestinationDir(artifacts));
            context.waitTicks(120);
            checkScene(context, initial, origin.get(), true);
            context.runOnClient(client -> AfkCinematics.stop());

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                noDrops(player, "Surface cinematic");
                cavern(player, origin.get());
                noDrops(player, "Cavern fixture");
            });
            context.waitFor(client -> client.level.getBlockState(origin.get().above(70)).is(Blocks.STONE));
            context.waitTicks(30);
            context.runOnClient(client -> AfkCinematics.config().subject = CinematicConfig.Subject.SCENERY);
            start(context);
            context.waitFor(client -> AfkCinematics.scenery() && AfkCinematics.fade() == 0, 160);
            checkScene(context, initial, origin.get(), false);
            context.takeScreenshot(TestScreenshotOptions.of("scenery-underground-cavern").withDestinationDir(artifacts));
            context.waitTicks(80);
            checkScene(context, initial, origin.get(), false);
            var obstruct = context.computeOnClient(client -> BlockPos.containing(camera(client).position()));
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().level()
                .setBlockAndUpdate(obstruct, Blocks.STONE.defaultBlockState()));
            context.waitFor(client -> !AfkCinematics.ownsCamera(), 40);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.getInventory().countItem(Items.EMERALD) == 32, "Scenery changed server inventory");
                require(player.position().distanceToSqr(WorldGeometry.vector(initial.position())) < 0.0001,
                    "Scenery changed the server player position");
                noDrops(player, "Cavern cinematic");
            });
            try { Files.write(artifacts.resolve("scenery-frames.csv"), sceneryFrames); }
            catch (Exception exception) { throw new AssertionError(exception); }
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                holdCoveredScenery = null;
                sceneryFrames = null;
                hide(client, hidden);
                focused = null;
            });
        }
    }

    private static void checkScene(ClientGameTestContext context, Pose initial, BlockPos origin, boolean surface) {
        context.runOnClient(client -> {
            var point = WorldGeometry.point(camera(client).position());
            require(AfkCinematics.scenery() && AfkCinematics.fade() == 0, "Scenery view stopped before inspection");
            require(Scene.inRange(point, initial.position()), "Scenery escaped the player-centered range");
            require(new WorldGeometry(client.level).clear(point, point), "Scenery camera clipped terrain");
            require(surface ? point.y() > origin.getY() + 10 : point.y() < origin.getY() + 9,
                "Scenery did not use the expected surface/cavern viewpoint");
            require(WorldGeometry.point(client.player.position()).distance(initial.position()) < 0.0001
                && client.player.getYRot() == initial.yaw() && client.player.getXRot() == initial.pitch(),
                "Scenery altered the player pose");
        });
    }

    private static void surface(ServerPlayer player, BlockPos origin) {
        for (BlockPos block : BlockPos.betweenClosed(origin.offset(-18, 0, -18), origin.offset(18, 10, 18))) {
            if (playerCell(block, origin)) continue;
            player.level().setBlockAndUpdate(block, (block.getY() == origin.getY() + 10 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState());
        }
        player.level().setBlockAndUpdate(origin.above(), Blocks.AIR.defaultBlockState());
        player.level().setBlockAndUpdate(origin.above(2), Blocks.AIR.defaultBlockState());
        for (BlockPos block : BlockPos.betweenClosed(origin.offset(5, 10, 5), origin.offset(9, 10, 9)))
            player.level().setBlockAndUpdate(block, Blocks.WATER.defaultBlockState());
        for (BlockPos block : BlockPos.betweenClosed(origin.offset(-10, 14, 6), origin.offset(-6, 16, 10)))
            player.level().setBlockAndUpdate(block, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        for (int y = 11; y <= 15; y++) player.level().setBlockAndUpdate(origin.offset(-8, y, 8), Blocks.OAK_LOG.defaultBlockState());
        for (int x = -10; x <= 10; x += 2) player.level().setBlockAndUpdate(origin.offset(x, 11, -8), Blocks.POPPY.defaultBlockState());
    }

    private static void cavern(ServerPlayer player, BlockPos origin) {
        // Skip plant survival checks while replacing the fixture's garden.
        for (BlockPos block : BlockPos.betweenClosed(origin.offset(-18, 1, -18), origin.offset(18, 20, 18))) {
            if (playerCell(block, origin)) continue;
            player.level().setBlock(block, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        player.level().setBlockAndUpdate(origin.above(), Blocks.AIR.defaultBlockState());
        player.level().setBlockAndUpdate(origin.above(2), Blocks.AIR.defaultBlockState());
        for (BlockPos block : BlockPos.betweenClosed(origin.offset(3, 1, -8), origin.offset(14, 8, 8)))
            player.level().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        for (BlockPos block : BlockPos.betweenClosed(origin.offset(3, 0, -8), origin.offset(14, 0, 8)))
            player.level().setBlockAndUpdate(block, Blocks.MOSS_BLOCK.defaultBlockState());
        for (int z = -6; z <= 6; z += 3) player.level().setBlockAndUpdate(origin.offset(12, 8, z), Blocks.GLOWSTONE.defaultBlockState());
        for (BlockPos block : BlockPos.betweenClosed(origin.offset(-22, 70, -22), origin.offset(22, 70, 22)))
            player.level().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
    }

    private static boolean playerCell(BlockPos block, BlockPos origin) {
        return block.getX() == origin.getX() && block.getZ() == origin.getZ()
            && block.getY() > origin.getY() && block.getY() <= origin.getY() + 2;
    }

    private static void noDrops(ServerPlayer player, String stage) {
        var items = player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32));
        require(items.isEmpty(), stage + " created items: " + items.stream().map(entity -> entity.getItem().toString()).toList());
    }

    private static void assertBlack(Path path) {
        try {
            var image = ImageIO.read(path.toFile());
            for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x += 8)
                for (int y = image.getHeight() / 4; y < image.getHeight() * 3 / 4; y += 8)
                    require((image.getRGB(x, y) & 0xffffff) == 0, "Covered transition exposed the world with F1 enabled");
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

}
