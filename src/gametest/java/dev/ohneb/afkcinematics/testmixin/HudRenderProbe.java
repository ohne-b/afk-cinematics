package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.ToastGameTest;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class HudRenderProbe {
    //? if <26.2 {
    //? if >=26.1 {
    /*@Inject(method = "extract", at = @At("HEAD"))
    *///?} else {
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    //?}
    private void afkCinematics$rawHud(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        if (AfkCinematics.hideHud()) ToastGameTest.rawHudHidden = Minecraft.getInstance().options.hideGui;
        if (ToastGameTest.failRender) throw new ToastGameTest.RenderFailure();
    }
    //?}
}
