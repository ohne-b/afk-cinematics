package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "team.creative.ambientsounds.environment.AmbientEnvironment", remap = false)
public abstract class AmbientSoundsMixin {
    //? if >=26.3 {
    /*@Redirect(method = "isEyeInFluid", remap = false, at = @At(value = "INVOKE", remap = true,
    *///?} else {
    @Redirect(method = "analyzeUnderwater", remap = false, at = @At(value = "INVOKE", remap = true,
    //?}
        target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean afkCinematics$water(Player player, TagKey<Fluid> tag) {
        return AfkCinematics.ambientFluid(player, tag);
    }

    @Redirect(method = "analyzeUnderwater", remap = false, at = @At(value = "INVOKE", remap = true,
        target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 afkCinematics$depthOrigin(Player player) {
        return AfkCinematics.ambientPosition(player);
    }
}
