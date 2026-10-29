package dev.ohneb.afkcinematics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "me.ramidzkh.fabrishot.Fabrishot", remap = false)
public abstract class FabrishotMixin {
    @Unique private static Runnable afkCinematics$release;

    @WrapOperation(method = "startCapture", at = @At(value = "INVOKE", target = "Lme/ramidzkh/fabrishot/Fabrishot;refresh()V"), remap = false, require = 0)
    private static void afkCinematics$start(Operation<Void> original) {
        Runnable release = AfkCinematics.holdForScreenshot();
        try {
            original.call();
            afkCinematics$release = release;
        } catch (RuntimeException | Error failure) {
            release.run();
            throw failure;
        }
    }

    @WrapOperation(method = "onRenderPreOrPost", at = @At(value = "INVOKE", target = "Lme/ramidzkh/fabrishot/Fabrishot;refresh()V"), remap = false, require = 0)
    private static void afkCinematics$finish(Operation<Void> original) {
        try { original.call(); }
        finally {
            if (afkCinematics$release != null) {
                afkCinematics$release.run();
                afkCinematics$release = null;
            }
        }
    }
}
