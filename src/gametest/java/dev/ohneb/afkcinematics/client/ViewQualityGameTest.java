package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Vec;
import dev.ohneb.afkcinematics.core.ViewQuality;
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
                require(WorldScenery.depth(client.level, vantage, direction) == ViewQuality.RANGE,
                    "Open view did not reach the bounded sampling distance");
                double open = WorldScenery.quality(client.level, vantage, focus, true);
                require(open > 0, "Loaded open view was rejected");
                try {
                    client.level.setBlock(block, Blocks.STONE.defaultBlockState(), 3);
                    require(Math.abs(WorldScenery.depth(client.level, vantage, direction) - 1) < 0.001,
                        "Depth sample missed the close wall");
                    require(WorldScenery.quality(client.level, vantage, focus, true) < open,
                        "Close foreground wall outranked the open view");
                    client.level.setBlock(block, Blocks.WATER.defaultBlockState(), 3);
                    require(WorldScenery.depth(client.level, vantage, direction) < 2,
                        "Depth sample treated water as empty scenery");
                } finally {
                    client.level.setBlock(block, old, 3);
                }
                Vec unloaded = vantage.add(new Vec(1_000_000, 0, 0));
                var geometry = new WorldGeometry(client.level);
                require(!geometry.clear(unloaded, unloaded), "Camera clearance accepted an unloaded chunk");
                require(!geometry.visible(unloaded, unloaded.add(direction.scale(8)), 0.1),
                    "Swept visibility accepted unloaded terrain");
                require(WorldScenery.depth(client.level, unloaded, direction) == -1,
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
        } finally {
            context.runOnClient(client -> focused = null);
        }
    }
}
