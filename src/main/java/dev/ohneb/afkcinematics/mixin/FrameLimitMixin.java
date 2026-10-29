package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
//? if >=1.21.2 {
import com.mojang.blaze3d.platform.FramerateLimitTracker;
//?} else {
/*import net.minecraft.client.Minecraft;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Run before Dynamic FPS's cancellable cap callback.
//? if >=1.21.2 {
@Mixin(value = FramerateLimitTracker.class, priority = 900)
//?} else {
/*@Mixin(value = Minecraft.class, priority = 900)
*///?}
abstract class FrameLimitMixin {
    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void cinematicLimit(CallbackInfoReturnable<Integer> callback) {
        int limit = AfkCinematics.frameLimit();
        if (limit > 0) callback.setReturnValue(limit);
    }
}
