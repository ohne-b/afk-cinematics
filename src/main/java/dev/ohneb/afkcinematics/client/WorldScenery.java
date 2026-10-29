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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
        double distance = Math.min(12, depth(level, vantage, direction, false) - 0.5);
        if (distance < 3) return null;
        Vec focus = vantage.add(direction.scale(distance));
        double quality = quality(level, vantage, focus, false, false);
        var scene = new Scene(focus, vantage, Scene.Kind.LANDSCAPE, quality);
        return quality > 0 && scene.near(anchor) && inside(level, focus) && geometry.visible(vantage, focus) ? scene : null;
    }

    private static Scene surface(ClientLevel level, WorldGeometry geometry, Vec anchor, double bearing, int index) {
        double angle = Math.toRadians(bearing + index % 8 * 45);
        double radius = index < 8 ? 8 : 16;
        int centerX = (int)Math.floor(anchor.x() + Math.sin(angle) * radius);
        int centerZ = (int)Math.floor(anchor.z() + Math.cos(angle) * radius);
        Scene best = null;
        double viewAngle = angle + Math.PI + Math.toRadians(25);
        boolean low = index % 2 == 1;
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
                if (low && state.getFluidState().is(FluidTags.WATER)) {
                    Scene water = underwater(level, geometry, anchor, block, viewAngle);
                    if (water != null && (best == null || water.interest() > best.interest())) best = water;
                    continue;
                }
                if (low) {
                    block = ground(level, block);
                    if (block == null) continue;
                    y = block.getY();
                    state = level.getBlockState(block);
                }
                var shape = state.getVisualShape(level, block, CollisionContext.empty());
                double top = shape.isEmpty() ? 0.3 : shape.max(Direction.Axis.Y);
                if (!state.getFluidState().isEmpty()) top = Math.max(top, state.getFluidState().getHeight(level, block));
                Vec target = new Vec(x + 0.5, y + top + 0.35, z + 0.5);
                if (Math.hypot(target.x() - anchor.x(), target.z() - anchor.z()) > 18) continue;
                double distance = low ? 8 : 12;
                Vec vantage = target.add(new Vec(Math.sin(viewAngle) * distance, low ? 0.3 : 2, Math.cos(viewAngle) * distance));
                BlockPos column = BlockPos.containing(vantage.x(), vantage.y(), vantage.z());
                if (!level.getChunkSource().hasChunk(column.getX() >> 4, column.getZ() >> 4)) continue;
                double floor = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
                if (low) {
                    BlockPos ground = ground(level, new BlockPos(column.getX(), (int)floor - 1, column.getZ()));
                    if (ground == null) continue;
                    var floorShape = level.getBlockState(ground).getVisualShape(level, ground, CollisionContext.empty());
                    floor = ground.getY() + (floorShape.isEmpty() ? 1 : floorShape.max(Direction.Axis.Y));
                }
                vantage = new Vec(vantage.x(), Math.max(vantage.y(), floor + (low ? 0.6 : 1.5)), vantage.z());
                Scene.Kind kind = low ? Scene.Kind.GROUND : Scene.Kind.LANDSCAPE;
                Scene scene = new Scene(target, vantage, kind, 0);
                if (!scene.near(anchor) || !inside(level, vantage) || !geometry.clear(vantage, vantage)
                    || !geometry.visible(vantage, target)) continue;
                double quality = quality(level, vantage, target, true, false);
                if (quality <= 0) continue;
                double score = quality + interest(state) * 0.08 + (low ? 0.75 : 0);
                if (best == null || score > best.interest()) best = new Scene(target, vantage, kind, score);
            }
        }
        return best;
    }

    private static BlockPos ground(ClientLevel level, BlockPos top) {
        for (int depth = 0; depth < 24; depth++) {
            BlockPos block = top.below(depth);
            if (level.isOutsideBuildHeight(block)) return null;
            BlockState state = level.getBlockState(block);
            if (!state.getFluidState().isEmpty()) return null;
            if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                && !state.getCollisionShape(level, block, CollisionContext.empty()).isEmpty()) return block;
        }
        return null;
    }

    private static Scene underwater(ClientLevel level, WorldGeometry geometry, Vec anchor, BlockPos surface, double angle) {
        double waterTop = surface.getY() + level.getFluidState(surface).getHeight(level, surface);
        for (int depth = 1; depth <= 12; depth++) {
            BlockPos block = surface.below(depth);
            if (level.isOutsideBuildHeight(block)) return null;
            BlockState state = level.getBlockState(block);
            if (state.getFluidState().is(FluidTags.WATER)) continue;
            var shape = state.getVisualShape(level, block, CollisionContext.empty());
            if (depth < 3 || shape.isEmpty()) return null;
            Vec focus = new Vec(block.getX() + 0.5, block.getY() + shape.max(Direction.Axis.Y) + 1, block.getZ() + 0.5);
            Vec vantage = new Vec(focus.x() + Math.sin(angle) * 6, Math.min(waterTop - 1.2, focus.y() + 1.3),
                focus.z() + Math.cos(angle) * 6);
            var scene = new Scene(focus, vantage, Scene.Kind.WATER, 0);
            var wet = geometry.forScene(scene);
            if (!scene.near(anchor) || !inside(level, vantage) || !wet.clear(vantage, vantage) || !wet.visible(vantage, focus)) return null;
            double quality = quality(level, vantage, focus, false, true);
            return quality > 0 ? new Scene(focus, vantage, Scene.Kind.WATER, quality + 1.5) : null;
        }
        return null;
    }

    static double quality(ClientLevel level, Vec vantage, Vec focus, boolean outdoors, boolean water) {
        var client = Minecraft.getInstance();
        var window = client.getWindow();
        Pose pose = Pose.lookingAt(vantage, focus);
        double aspect = (double)window.getWidth() / Math.max(1, window.getHeight());
        Vec[] rays = ViewQuality.directions(pose, AfkCinematics.config().lens.fieldOfView(client.options.fov().get(), aspect),
            aspect, AfkCinematics.config().letterbox);
        double[] depths = new double[rays.length];
        for (int i = 0; i < rays.length; i++) depths[i] = depth(level, vantage, rays[i], water);
        return ViewQuality.score(depths, pose.pitch(), outdoors);
    }

    static double depth(ClientLevel level, Vec vantage, Vec direction, boolean water) {
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
            ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, CollisionContext.empty()) {
            @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter world, BlockPos position) {
                return water && fluid.is(FluidTags.WATER) ? Shapes.empty() : super.getFluidShape(fluid, world, position);
            }
        });
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
