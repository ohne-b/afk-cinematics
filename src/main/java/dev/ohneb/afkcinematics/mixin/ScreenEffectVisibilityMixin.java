package dev.ohneb.afkcinematics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectVisibilityMixin {
    //? if >=1.21.6 && <26.1 {
    @ModifyExpressionValue(method = "renderScreenEffect",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z", opcode = 180))
    private static boolean afkCinematics$hidden(boolean original) {
        return original || AfkCinematics.hideHud();
    }
    //?}
}
