package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Vec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;

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
        return new Vec(hook.xOld + (hook.getX() - hook.xOld) * delta,
            hook.yOld + (hook.getY() - hook.yOld) * delta + .1,
            hook.zOld + (hook.getZ() - hook.zOld) * delta).subtract(player);
    }

    static boolean canUse(LocalPlayer player, HitResult hit) {
        // Block/entity interactions precede rod use; an offhand rod cannot authorize another held item.
        return hit != null && hit.getType() == HitResult.Type.MISS
            && (player.getMainHandItem().is(Items.FISHING_ROD)
                || player.getMainHandItem().isEmpty() && player.getOffhandItem().is(Items.FISHING_ROD));
    }
}
