package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Geometry;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Vec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WorldGeometry implements Geometry {
    private static final double RADIUS = 0.25;
    private final ClientLevel level;

    public WorldGeometry(ClientLevel level) { this.level = level; }

    @Override public Scene scenery(Vec anchor, Pose original, double bearing, int probe) {
        return WorldScenery.probe(level, this, anchor, original, bearing, probe);
    }

    @Override
    public boolean clear(Vec from, Vec to, double margin) {
        int steps = Math.max(1, (int) Math.ceil(from.distance(to) / 0.25));
        if (steps > 64) return false;
        Vec previous = from;
        for (int i = 1; i <= steps; i++) {
            Vec next = from.lerp(to, (double) i / steps);
            AABB box = new AABB(vector(previous), vector(next)).inflate(RADIUS + margin);
            if (!level.getWorldBorder().isWithinBounds(box)) return false;
            var volume = Shapes.create(box);
            // Moving pistons and other shapes can extend beyond their owning block.
            for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ).offset(-1, -1, -1),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ).offset(1, 1, 1))) {
                if (!level.hasChunkAt(block)) return false;
                var state = level.getBlockState(block);
                var shape = state.getVisualShape(level, block, CollisionContext.empty());
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(volume, shape.move(block.getX(), block.getY(), block.getZ()), BooleanOp.AND)) return false;
                var fluid = state.getFluidState();
                if (!fluid.isEmpty() && Shapes.joinIsNotEmpty(volume,
                    fluid.getShape(level, block).move(block.getX(), block.getY(), block.getZ()), BooleanOp.AND)) return false;
            }
            previous = next;
        }
        return true;
    }

    @Override
    public boolean visible(Vec camera, Vec subject, double margin) {
        if (camera.distance(subject) + 2 * margin > 16) return false;
        Vec3 start = vector(camera), end = vector(subject);
        AABB ray = new AABB(start, end).inflate(margin);
        // A ray can cross a moving shape without crossing the block that owns it.
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(ray.minX, ray.minY, ray.minZ).offset(-1, -1, -1),
                BlockPos.containing(ray.maxX, ray.maxY, ray.maxZ).offset(1, 1, 1))) {
            if (!level.hasChunkAt(block)) return false;
            var state = level.getBlockState(block);
            if (intersects(state.getVisualShape(level, block, CollisionContext.empty()), block, start, end, margin)) return false;
            var fluid = state.getFluidState();
            if (!fluid.isEmpty() && intersects(fluid.getShape(level, block), block, start, end, margin)) return false;
        }
        return true;
    }

    private static boolean intersects(VoxelShape shape, BlockPos block, Vec3 start, Vec3 end, double margin) {
        if (margin == 0) return shape.clip(start, end, block) != null;
        for (AABB part : shape.toAabbs()) {
            AABB expanded = part.move(block.getX(), block.getY(), block.getZ()).inflate(margin);
            if (expanded.contains(start) || expanded.contains(end) || expanded.clip(start, end).isPresent()) return true;
        }
        return false;
    }

    public static Vec3 vector(Vec point) { return new Vec3(point.x(), point.y(), point.z()); }
    public static Vec point(Vec3 vector) { return new Vec(vector.x, vector.y, vector.z); }
}
