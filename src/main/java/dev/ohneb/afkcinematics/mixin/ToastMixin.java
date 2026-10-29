package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.2 {
@Mixin(net.minecraft.client.gui.components.toasts.ToastManager.class)
//?} else {
/*@Mixin(net.minecraft.client.gui.components.toasts.ToastComponent.class)
*///?}
public abstract class ToastMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$hide(CallbackInfo ci) {
        if (AfkCinematics.hideHud()) ci.cancel();
    }
}
