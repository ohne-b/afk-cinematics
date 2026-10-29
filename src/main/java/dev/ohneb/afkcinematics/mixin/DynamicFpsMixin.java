package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dynamic_fps.impl.DynamicFPSMod", remap = false, priority = 900)
abstract class DynamicFpsMixin {
    @Inject(method = "targetFrameRate", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void cinematicLimit(CallbackInfoReturnable<Integer> callback) {
        int limit = AfkCinematics.frameLimit();
        if (limit > 0) callback.setReturnValue(limit);
    }
}
