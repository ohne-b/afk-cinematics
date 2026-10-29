package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Vec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;

final class WorldScenery {
    private WorldScenery() { }

    static Scene probe(ClientLevel level, WorldGeometry geometry, Vec anchor, Pose original, double bearing, int index) {
        if (index < 16) return surface(level, geometry, anchor, bearing, index);
        Vec vantage;
        double angle;
        if (index < 24) {
            vantage = original.position();
            angle = Math.toRadians(bearing + (index - 16) * 45);
        } else {
            int local = index - 24;
            angle = Math.toRadians(bearing + local * 137.5);
            double radius = 6 + local % 3 * 3;
            vantage = anchor.add(new Vec(Math.sin(angle) * radius, (local / 3 % 3 - 1) * 8, Math.cos(angle) * radius));
            angle += Math.PI / 2;
        }
        if (!inside(level, vantage) || !geometry.clear(vantage, vantage)) return null;
        Vec direction = new Vec(Math.sin(angle), -0.08, Math.cos(angle));
        Vec focus = vantage;
        for (double distance = 0.5; distance <= 8; distance += 0.5) {
            Vec next = vantage.add(direction.scale(distance));
            if (!inside(level, next) || !geometry.visible(vantage, next)) break;
            focus = next;
        }
        if (focus.distance(vantage) < 2) return null;
        var scene = new Scene(focus, vantage, true, 1 + focus.distance(vantage) / 8);
        return scene.near(anchor) ? scene : null;
    }

    private static Scene surface(ClientLevel level, WorldGeometry geometry, Vec anchor, double bearing, int index) {
        double angle = Math.toRadians(bearing + index % 8 * 45);
        double radius = index < 8 ? 8 : 16;
        int centerX = (int)Math.floor(anchor.x() + Math.sin(angle) * radius);
        int centerZ = (int)Math.floor(anchor.z() + Math.cos(angle) * radius);
        Vec focus = null;
        double score = -1;
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                int x = centerX + dx, z = centerZ + dz;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                // WORLD_SURFACE is sent with the chunk; this does not build a new heightmap.
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (Math.abs(y - anchor.y()) > 51) continue;
                BlockPos block = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(block);
                if (state.isAir() || state.is(Blocks.BEDROCK)) continue;
                double interest = interest(state) - Math.hypot(dx, dz) * 0.03;
                if (interest <= score) continue;
                var shape = state.getVisualShape(level, block, CollisionContext.empty());
                double top = shape.isEmpty() ? 0.3 : shape.max(Direction.Axis.Y);
                if (!state.getFluidState().isEmpty()) top = Math.max(top, state.getFluidState().getHeight(level, block));
                Vec target = new Vec(x + 0.5, y + top + 0.35, z + 0.5);
                if (Math.hypot(target.x() - anchor.x(), target.z() - anchor.z()) > 18) continue;
                focus = target;
                score = interest;
            }
        }
        if (focus == null) return null;
        double viewAngle = angle + Math.PI + Math.toRadians(25);
        Vec vantage = focus.add(new Vec(Math.sin(viewAngle) * 8, 2.5, Math.cos(viewAngle) * 8));
        BlockPos column = BlockPos.containing(vantage.x(), vantage.y(), vantage.z());
        if (!level.hasChunkAt(column)) return null;
        double ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
        vantage = new Vec(vantage.x(), Math.max(vantage.y(), ground + 1.5), vantage.z());
        Scene scene = new Scene(focus, vantage, true, 10 + score);
        return scene.near(anchor) && inside(level, vantage) && geometry.clear(vantage, vantage)
            && geometry.visible(vantage, focus) ? scene : null;
    }

    private static boolean inside(ClientLevel level, Vec point) {
        BlockPos block = BlockPos.containing(point.x(), point.y(), point.z());
        return !level.isOutsideBuildHeight(block) && level.hasChunkAt(block);
    }

    private static double interest(BlockState state) {
        if (state.getFluidState().is(FluidTags.WATER)) return 6;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS)) return 5;
        if (state.is(BlockTags.LOGS)) return 4;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SNOW_BLOCK)
            || state.is(Blocks.SAND) || state.is(Blocks.ICE)) return 3;
        return 1;
    }
}
