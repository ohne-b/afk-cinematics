package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Vec;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class WorldGeometryGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var origin = client.player.blockPosition().above(5);
                var oldState = client.level.getBlockState(origin);
                var moving = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, Direction.EAST);
                var point = new Vec(origin.getX() - 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
                var geometry = new WorldGeometry(client.level);
                require(geometry.clear(point, point), "Fixture did not start in empty air");
                try {
                    client.level.setBlock(origin, moving, 3);
                    client.level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(origin, moving,
                        Blocks.STONE.defaultBlockState(), Direction.EAST, true, false));
                    require(!geometry.clear(point, point), "Camera missed a piston shape extending from the adjacent block");
                    client.level.removeBlockEntity(origin);
                    client.level.setBlock(origin, Blocks.WATER.defaultBlockState(), 3);
                    require(geometry.clear(point, point), "Adjacent water blocked a camera volume outside the fluid");
                    var wet = new Vec(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
                    require(!geometry.clear(wet, wet), "Camera volume was allowed inside water");
                } finally {
                    client.level.removeBlockEntity(origin);
                    client.level.setBlock(origin, oldState, 3);
                }
            });
        }
        context.runOnClient(client -> focused = null);
    }
}
