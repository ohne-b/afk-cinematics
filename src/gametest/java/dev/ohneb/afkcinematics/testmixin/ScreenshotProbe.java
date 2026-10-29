package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.TestSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screenshot.class)
public abstract class ScreenshotProbe {
    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V", at = @At("HEAD"))
    private static void afkCinematics$view(CallbackInfo ci) {
        if (!TestSupport.checkScreenshotView) return;
        var client = Minecraft.getInstance();
        TestSupport.require(AfkCinematics.showing(), "Screenshot readback lost the cinematic");
        TestSupport.require(TestSupport.camera(client).position().distanceTo(client.player.getEyePosition()) > 3,
            "Screenshot readback used the player's perspective");
        TestSupport.cinematicScreenshots++;
    }
}
