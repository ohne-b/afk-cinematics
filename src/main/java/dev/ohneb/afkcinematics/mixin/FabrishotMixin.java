package dev.ohneb.afkcinematics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "me.ramidzkh.fabrishot.Fabrishot", remap = false)
public abstract class FabrishotMixin {
    @Shadow public static boolean isInCapture() { throw new AssertionError(); }
    @Unique private static Runnable afkCinematics$release;

    @WrapMethod(method = "startCapture", remap = false)
    private static void afkCinematics$start(Operation<Void> original) {
        if (isInCapture()) { original.call(); return; }
        Runnable release = AfkCinematics.holdForScreenshot();
        try {
            original.call();
            if (isInCapture()) afkCinematics$release = release;
            else release.run();
        } catch (RuntimeException | Error failure) {
            release.run();
            throw failure;
        }
    }

    @WrapMethod(method = "onRenderPreOrPost", remap = false)
    private static void afkCinematics$finish(Operation<Void> original) {
        try { original.call(); }
        finally {
            if (!isInCapture() && afkCinematics$release != null) {
                afkCinematics$release.run();
                afkCinematics$release = null;
            }
        }
    }
}
