package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
//? if <1.21.2 {
/*import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Unique;
*///?}
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
    //? if >=1.21.2 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$hide(CallbackInfo ci) {
        if (AfkCinematics.hideHud()) ci.cancel();
    }
    //?} else {
    /*@Unique private boolean afkCinematics$clipped;

    @Inject(method = "render", at = @At("HEAD"))
    private void afkCinematics$clip(GuiGraphics graphics, CallbackInfo ci) {
        afkCinematics$clipped = AfkCinematics.hideHud();
        if (afkCinematics$clipped) {
            // Legacy toast rendering also advances timers, queues and sounds.
            graphics.flush();
            graphics.enableScissor(0, 0, 0, 0);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void afkCinematics$restoreClip(GuiGraphics graphics, CallbackInfo ci) {
        if (afkCinematics$clipped) {
            graphics.flush();
            graphics.disableScissor();
            afkCinematics$clipped = false;
        }
    }
    *///?}
}
