package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Vec;
import dev.ohneb.afkcinematics.core.ViewQuality;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.CinematicSession;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.block.Blocks;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ViewQualityGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(30);
            context.runOnClient(client -> {
                var block = client.player.blockPosition().above(15).east(1);
                var old = client.level.getBlockState(block);
                Vec vantage = new Vec(block.getX() - 1, block.getY() + 0.5, block.getZ() + 0.5);
                Vec direction = new Vec(1, 0, 0);
                Vec focus = vantage.add(direction.scale(12));
                require(WorldScenery.depth(client.level, vantage, direction, false) == ViewQuality.RANGE,
                    "Open view did not reach the bounded sampling distance");
                double open = WorldScenery.quality(client.level, vantage, focus, true, false);
                require(open > 0, "Loaded open view was rejected");
                try {
                    client.level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
                    require(Math.abs(WorldScenery.depth(client.level, vantage, direction, false) - 1) < 0.001,
                        "Depth sample missed the close wall");
                    require(WorldScenery.quality(client.level, vantage, focus, true, false) < open,
                        "Close foreground wall outranked the open view");
                    client.level.setBlock(block, Blocks.WATER.defaultBlockState(), 3);
                    require(WorldScenery.depth(client.level, vantage, direction, false) < 2,
                        "Depth sample treated water as empty scenery");
                } finally {
                    client.level.setBlock(block, old, 3);
                }
                Vec unloaded = vantage.add(new Vec(1_000_000, 0, 0));
                var geometry = new WorldGeometry(client.level);
                require(!geometry.clear(unloaded, unloaded), "Camera clearance accepted an unloaded chunk");
                require(!geometry.visible(unloaded, unloaded.add(direction.scale(8)), 0.1),
                    "Swept visibility accepted unloaded terrain");
                require(WorldScenery.depth(client.level, unloaded, direction, false) == -1,
                    "Unloaded terrain was counted as distant scenery");
                int chunkX = client.player.blockPosition().getX() >> 4;
                int chunkZ = client.player.blockPosition().getZ() >> 4;
                int edge = chunkX;
                while (edge < chunkX + 32 && client.level.getChunkSource().hasChunk(edge, chunkZ)) edge++;
                require(edge > chunkX && edge < chunkX + 32, "Fixture did not find the loaded chunk boundary");
                Vec border = new Vec((edge << 4) - 0.6, vantage.y(), (chunkZ << 4) + 4);
                require(!geometry.clear(border, border), "Camera owner halo crossed into an unloaded chunk");
                require(!geometry.visible(border, border.add(new Vec(0, 0, 2)), 0.2),
                    "Expanded sightline accepted an unloaded neighboring shape origin");
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                floor(player);
                //? if >=26.2 {
                /*var cow = net.minecraft.world.entity.EntityTypes.COW.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                *///?} else {
                var cow = net.minecraft.world.entity.EntityType.COW.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                //?}
                require(cow != null, "Missing wildlife fixture");
                cow.snapTo(player.getX() + 4, player.getY(), player.getZ() + 3, 110, 0);
                cow.setNoAi(true);
                player.level().addFreshEntity(cow);
            });
            context.waitTicks(30);
            context.runOnClient(client -> {
                var config = AfkCinematics.config();
                config.subject = CinematicConfig.Subject.SCENERY;
                config.shots = java.util.EnumSet.of(Shot.Type.DRIFT);
                config.wildlife = false;
            });
            start(context);
            for (int i = 0; i < 3; i++) {
                context.waitFor(client -> cameraOwner() instanceof CinematicSession session && session.settled(), 240);
                if (i == 2) break;
                int take = context.computeOnClient(client -> ((CinematicSession)cameraOwner()).take());
                context.runOnClient(client -> AfkCinematics.nextShot());
                context.waitFor(client -> cameraOwner() instanceof CinematicSession session && session.take() > take, 240);
            }
            context.runOnClient(client -> {
                AfkCinematics.config().wildlife = true;
                try {
                    var field = AfkCinematics.class.getDeclaredField("RANDOM");
                    field.setAccessible(true);
                    ((java.util.Random)field.get(null)).setSeed(3);
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                AfkCinematics.nextShot();
            });
            context.waitFor(client -> scene() != null && scene().animal() != null && AfkCinematics.fade() == 0, 240);
            Scene wildlife = context.computeOnClient(client -> scene());
            context.runOnClient(client -> {
                var geometry = new WorldGeometry(client.level);
                require(geometry.available(wildlife, WorldGeometry.point(camera(client).position())), "Revealed wildlife has no live visible animal");
                Vec anchor = WorldGeometry.point(client.player.position().add(0, client.player.getBbHeight() * .65, 0));
                for (int probe = 0; probe < 8; probe++) require(geometry.wildlife(anchor, 0, wildlife.animal().species(), probe) == null,
                    "The same species remained eligible for the next wildlife view");
            });
            context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("afk-wildlife")
                .withDestinationDir(java.nio.file.Path.of(System.getProperty("afkcinematics.test.artifacts"))));
            world.getServer().runOnServer(server -> {
                var animal = server.overworld().getEntity(wildlife.animal().uuid());
                require(animal != null, "Wildlife fixture lost its server animal");
                animal.discard();
            });
            context.waitFor(client -> scene() != null && scene().animal() == null && AfkCinematics.showing(), 240);
        } finally {
            context.runOnClient(client -> { AfkCinematics.stop(); focused = null; });
        }
    }

    private static Scene scene() {
        if (cameraOwner() == null) return null;
        try {
            var field = CinematicSession.class.getDeclaredField("scene");
            field.setAccessible(true);
            return (Scene)field.get(cameraOwner());
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
