package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Geometry;
import dev.ohneb.afkcinematics.core.Vec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;

public final class WorldGeometry implements Geometry {
    private static final double RADIUS = 0.25;
    private final ClientLevel level;

    public WorldGeometry(ClientLevel level) { this.level = level; }

    @Override
    public boolean clear(Vec from, Vec to) {
        int steps = Math.max(1, (int) Math.ceil(from.distance(to) / 0.25));
        if (steps > 64) return false;
        Vec previous = from;
        for (int i = 1; i <= steps; i++) {
            Vec next = from.lerp(to, (double) i / steps);
            AABB box = new AABB(vector(previous), vector(next)).inflate(RADIUS);
            if (!level.getWorldBorder().isWithinBounds(box)) return false;
            var volume = Shapes.create(box);
            for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
                if (!level.hasChunkAt(block)) return false;
                var state = level.getBlockState(block);
                var shape = state.getVisualShape(level, block, CollisionContext.empty());
                if (!shape.isEmpty() && Shapes.joinIsNotEmpty(volume, shape.move(block.getX(), block.getY(), block.getZ()), BooleanOp.AND)) return false;
                if (!state.getFluidState().isEmpty()) return false;
            }
            previous = next;
        }
        return true;
    }

    @Override
    public boolean visible(Vec camera, Vec subject) {
        int steps = Math.max(1, (int) Math.ceil(camera.distance(subject)));
        if (steps > 16) return false;
        for (int i = 0; i <= steps; i++) {
            Vec point = camera.lerp(subject, (double) i / steps);
            if (!level.hasChunkAt(BlockPos.containing(point.x(), point.y(), point.z()))) return false;
        }
        return level.clip(new ClipContext(vector(camera), vector(subject), ClipContext.Block.VISUAL,
            ClipContext.Fluid.ANY, net.minecraft.client.Minecraft.getInstance().player)).getType() == HitResult.Type.MISS;
    }

    public static Vec3 vector(Vec point) { return new Vec3(point.x(), point.y(), point.z()); }
    public static Vec point(Vec3 vector) { return new Vec(vector.x, vector.y, vector.z); }
}
