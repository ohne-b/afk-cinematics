package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Vec;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class WideSceneryGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                floor(player);
                var base = player.blockPosition().below();
                for (BlockPos p : BlockPos.betweenClosed(base.offset(-26, 0, -26), base.offset(26, 20, 26)))
                    player.level().setBlock(p, (p.getY() == base.getY() ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(30);
            context.runOnClient(client -> {
                var geometry = new WorldGeometry(client.level);
                Vec anchor = WorldGeometry.point(client.player.position());
                Pose entry = new Pose(WorldGeometry.point(client.player.getEyePosition()), 0, 0);
                boolean high = false, low = false;
                for (int index = 0; index < Scene.PROBES; index++) {
                    Scene scene = geometry.scenery(anchor, entry, 0, index);
                    if (scene == null) continue;
                    high |= scene.kind() == Scene.Kind.AERIAL && scene.vantage().y() - scene.focus().y() >= 10
                        && scene.vantage().distance(scene.focus()) > 20;
                    low |= scene.kind() == Scene.Kind.GROUND;
                }
                require(high && low, "Wide scenery search lost high or low viewpoints");
                Vec start = anchor.add(new Vec(-10, 12, -10)), end = anchor.add(new Vec(10, 2, 10));
                require(geometry.visible(start, end, 0.1), "Clear long diagonal was rejected");
                require(!geometry.visible(start, start.add(new Vec(33, 0, 0))), "Sightline exceeded its bounded range");
                BlockPos blocker = BlockPos.containing(WorldGeometry.vector(start.lerp(end, 0.5)));
                var old = client.level.getBlockState(blocker);
                try {
                    client.level.setBlock(blocker, Blocks.STONE.defaultBlockState(), 3);
                    require(!geometry.visible(start, end, 0.1), "Long-ray corridor skipped its central obstruction");
                } finally { client.level.setBlock(blocker, old, 3); }
            });
        } finally {
            context.runOnClient(client -> { AfkCinematics.stop(); focused = null; });
        }
    }
}
