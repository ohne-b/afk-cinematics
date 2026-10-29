package dev.ohneb.afkcinematics.mixin;

//? if >=1.21.5 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
*///?}
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.5 {
import java.util.function.Consumer;
//?}

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    //? if >=1.21.6 {
    @WrapMethod(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V")
    private static void afkCinematics$capture(RenderTarget target, int scale, Consumer<NativeImage> consumer, Operation<Void> original) {
    //?} else if >=1.21.5 {
    /*@WrapMethod(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V")
    private static void afkCinematics$capture(RenderTarget target, Consumer<NativeImage> consumer, Operation<Void> original) {
    *///?} else {
    /*@WrapOperation(method = "_grab", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Screenshot;takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private static NativeImage afkCinematics$capture(RenderTarget target, Operation<NativeImage> original) {
    *///?}
        Runnable release = AfkCinematics.holdForScreenshot();
        //? if >=1.21.5 {
        Consumer<NativeImage> captured = image -> {
            try { consumer.accept(image); }
            finally { release.run(); }
        };
        try {
            //? if >=1.21.6 {
            original.call(target, scale, captured);
            //?} else {
            /*original.call(target, captured);
            *///?}
        } catch (RuntimeException | Error failure) {
            release.run();
            throw failure;
        }
        //?} else {
        /*try { return original.call(target); }
        finally { release.run(); }
        *///?}
    }
}
