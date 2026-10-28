package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$perspective(CallbackInfoReturnable<CameraType> cir) {
        if (AfkCinematics.showing()) cir.setReturnValue(CameraType.THIRD_PERSON_BACK);
    }
}
