package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Vec;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;

final class FishingActivity {
    private FishingActivity() { }

    static FishingHook hook(LocalPlayer player, ClientLevel world) {
        if (player == null || world == null) return null;
        var hook = player.fishing;
        if (hook == null || hook.isRemoved() || hook.level() != world || hook.getPlayerOwner() != player
            || hook.getHookedIn() != null || hook.distanceToSqr(player) > 24 * 24
            || !player.getMainHandItem().is(Items.FISHING_ROD) && !player.getOffhandItem().is(Items.FISHING_ROD)) return null;
        var block = hook.blockPosition();
        if (!world.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)) return null;
        // Vanilla's bobbing motion can lift the float just above the water's owning cell.
        for (int below = 0; below <= 1; below++) {
            var position = block.below(below);
            var fluid = world.getFluidState(position);
            double height = fluid.getHeight(world, position);
            if (fluid.is(FluidTags.WATER) && height > 0 && hook.getY() <= position.getY() + height + .25) return hook;
        }
        return null;
    }

    static Vec target(FishingHook hook, float delta, Vec player) {
        double x = hook.xOld + (hook.getX() - hook.xOld) * delta;
        double y = hook.yOld + (hook.getY() - hook.yOld) * delta;
        double z = hook.zOld + (hook.getZ() - hook.zOld) * delta;
        BlockPos base = BlockPos.containing(x, y - .25, z);
        // Frame the cast at the waterline, so a bite can dip without nodding the camera.
        double surface = y + .1;
        if (!hook.level().getChunkSource().hasChunk(base.getX() >> 4, base.getZ() >> 4))
            return new Vec(x, surface, z).subtract(player);
        for (int up = 0; up < 3; up++) {
            BlockPos block = base.above(up);
            var fluid = hook.level().getFluidState(block);
            if (!fluid.is(FluidTags.WATER)) break;
            surface = block.getY() + fluid.getHeight(hook.level(), block) + .12;
        }
        return new Vec(x, surface, z).subtract(player);
    }

}
