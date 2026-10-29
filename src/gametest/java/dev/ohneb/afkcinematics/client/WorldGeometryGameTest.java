package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Shot;
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
                    require(!geometry.visible(point.add(new Vec(0, 0, -2)), point.add(new Vec(0, 0, 2))),
                        "Sightline missed a moving shape whose owning block is outside the ray");
                    client.level.removeBlockEntity(origin);
                    client.level.setBlock(origin, Blocks.WATER.defaultBlockState(), 3);
                    require(geometry.clear(point, point), "Adjacent water blocked a camera volume outside the fluid");
                    require(!geometry.clear(point, point, 0.3), "Curved camera margin missed adjacent water");
                    require(geometry.visible(point.add(new Vec(0, 0, -2)), point.add(new Vec(0, 0, 2))),
                        "Adjacent water blocked an outside sightline");
                    require(!geometry.visible(point.add(new Vec(0, 0, -2)), point.add(new Vec(0, 0, 2)), 0.6),
                        "Swept sightline margin missed adjacent water");
                    var wet = new Vec(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5);
                    require(!geometry.clear(wet, wet), "Camera volume was allowed inside water");
                    client.level.setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
                    var shift = new Vec(origin.getX() - 2, origin.getY() - 70, origin.getZ() + 15);
                    var focus = new Vec(2.5, 70.35, -16.5).add(shift);
                    // A recorded crane shot grazed a leaf corner between the sixteen planning samples.
                    var shot = new Shot(Shot.Type.CRANE,
                        new Vec(-0.23616114660534882, 72.85, -8.982459033712733).add(shift),
                        new Vec(-0.04822262244816722, 73.68999999999998, -8.914055005047599).add(shift),
                        new Vec(0.13971590170901482, 74.05, -8.845650976382466).add(shift),
                        focus, focus, 14.8045577545392);
                    for (int sample = 0; sample <= 16; sample++) {
                        require(geometry.visible(shot.sample(shot.duration() * sample / 16).position(), focus),
                            "Fixture no longer falls between the original visibility samples");
                    }
                    require(!geometry.visible(shot.sample(shot.duration() * 0.525).position(), focus),
                        "Fixture did not reproduce the brief corner occlusion");
                    require(!geometry.valid(shot, shot.start()), "Planner accepted an occlusion between samples");
                    client.level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
                    require(geometry.valid(shot, shot.start()), "Unobstructed crane shot was rejected");
                } finally {
                    client.level.removeBlockEntity(origin);
                    client.level.setBlock(origin, oldState, 3);
                }
            });
        }
        context.runOnClient(client -> focused = null);
    }
}
