package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Vec;
import dev.ohneb.afkcinematics.core.ViewQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.HitResult;

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
        direction = direction.scale(1 / direction.length());
        double distance = Math.min(12, depth(level, vantage, direction) - 0.5);
        if (distance < 3) return null;
        Vec focus = vantage.add(direction.scale(distance));
        double quality = quality(level, vantage, focus, false);
        var scene = new Scene(focus, vantage, true, quality);
        return quality > 0 && scene.near(anchor) && inside(level, focus) && geometry.visible(vantage, focus) ? scene : null;
    }

    private static Scene surface(ClientLevel level, WorldGeometry geometry, Vec anchor, double bearing, int index) {
        double angle = Math.toRadians(bearing + index % 8 * 45);
        double radius = index < 8 ? 8 : 16;
        int centerX = (int)Math.floor(anchor.x() + Math.sin(angle) * radius);
        int centerZ = (int)Math.floor(anchor.z() + Math.cos(angle) * radius);
        Scene best = null;
        double viewAngle = angle + Math.PI + Math.toRadians(25);
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                int x = centerX + dx, z = centerZ + dz;
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                // WORLD_SURFACE is sent with the chunk; this does not build a new heightmap.
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (Math.abs(y - anchor.y()) > 51) continue;
                BlockPos block = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(block);
                if (state.isAir() || state.is(Blocks.BEDROCK)) continue;
                var shape = state.getVisualShape(level, block, CollisionContext.empty());
                double top = shape.isEmpty() ? 0.3 : shape.max(Direction.Axis.Y);
                if (!state.getFluidState().isEmpty()) top = Math.max(top, state.getFluidState().getHeight(level, block));
                Vec target = new Vec(x + 0.5, y + top + 0.35, z + 0.5);
                if (Math.hypot(target.x() - anchor.x(), target.z() - anchor.z()) > 18) continue;
                Vec vantage = target.add(new Vec(Math.sin(viewAngle) * 12, 2, Math.cos(viewAngle) * 12));
                BlockPos column = BlockPos.containing(vantage.x(), vantage.y(), vantage.z());
                if (!level.getChunkSource().hasChunk(column.getX() >> 4, column.getZ() >> 4)) continue;
                double ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
                vantage = new Vec(vantage.x(), Math.max(vantage.y(), ground + 1.5), vantage.z());
                Scene scene = new Scene(target, vantage, true, 0);
                if (!scene.near(anchor) || !inside(level, vantage) || !geometry.clear(vantage, vantage)
                    || !geometry.visible(vantage, target)) continue;
                double quality = quality(level, vantage, target, true);
                if (quality <= 0) continue;
                double score = quality + interest(state) * 0.08;
                if (best == null || score > best.interest()) best = new Scene(target, vantage, true, score);
            }
        }
        return best;
    }

    static double quality(ClientLevel level, Vec vantage, Vec focus, boolean outdoors) {
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        Pose pose = Pose.lookingAt(vantage, focus);
        Vec[] rays = ViewQuality.directions(pose, client.options.fov().get(),
            (double)window.getWidth() / Math.max(1, window.getHeight()), AfkCinematics.config().letterbox);
        double[] depths = new double[rays.length];
        for (int i = 0; i < rays.length; i++) depths[i] = depth(level, vantage, rays[i]);
        return ViewQuality.score(depths, pose.pitch(), outdoors);
    }

    static double depth(ClientLevel level, Vec vantage, Vec direction) {
        Vec end = vantage.add(direction.scale(ViewQuality.RANGE));
        int minX = (int)Math.floor(Math.min(vantage.x(), end.x())) >> 4;
        int maxX = (int)Math.floor(Math.max(vantage.x(), end.x())) >> 4;
        int minZ = (int)Math.floor(Math.min(vantage.z(), end.z())) >> 4;
        int maxZ = (int)Math.floor(Math.max(vantage.z(), end.z())) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return -1;
            }
        }
        // These bounded rays rank the view; WorldGeometry independently proves safety.
        var hit = level.clip(new ClipContext(WorldGeometry.vector(vantage), WorldGeometry.vector(end),
            ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? ViewQuality.RANGE
            : hit.getLocation().distanceTo(WorldGeometry.vector(vantage));
    }

    private static boolean inside(ClientLevel level, Vec point) {
        BlockPos block = BlockPos.containing(point.x(), point.y(), point.z());
        return !level.isOutsideBuildHeight(block) && level.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4);
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
