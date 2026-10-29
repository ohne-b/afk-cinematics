package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.Camera;
//? if <26.1 {
import net.minecraft.client.renderer.GameRenderer;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=26.1 {
/*@Mixin(Camera.class)
*///?} else {
@Mixin(GameRenderer.class)
//?}
public abstract class LensMixin {
    @Shadow public abstract boolean isPanoramicMode();

    //? if >=26.1 {
    /*@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void afkCinematics$worldLens(float delta, CallbackInfoReturnable<Float> cir) {
        if (!isPanoramicMode()) cir.setReturnValue((float)AfkCinematics.fieldOfView((Camera)(Object)this, cir.getReturnValueF()));
    }
    *///?} else if <1.21.2 {
    /*@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void afkCinematics$worldLens(Camera camera, float delta, boolean world, CallbackInfoReturnable<Double> cir) {
        if (world && !isPanoramicMode()) cir.setReturnValue(AfkCinematics.fieldOfView(camera, cir.getReturnValueD()));
    }
    *///?} else {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void afkCinematics$worldLens(Camera camera, float delta, boolean world, CallbackInfoReturnable<Float> cir) {
        if (world && !isPanoramicMode()) cir.setReturnValue((float)AfkCinematics.fieldOfView(camera, cir.getReturnValueF()));
    }
    //?}
}
