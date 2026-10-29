package dev.ohneb.afkcinematics.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingHook.class)
public interface FishingHookAccess {
    @Accessor("biting") boolean afkCinematics$biting();
}
