package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
//? if >=26.3 {
/*import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
*///?} else {
import net.minecraft.client.renderer.ItemInHandRenderer;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.3 {
/*@Mixin(FirstPersonHandsAndItemsRenderer.class)
*///?} else {
@Mixin(ItemInHandRenderer.class)
//?}
public abstract class HandMixin {
    //? if >=26.2 {
    /*@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    //?}
    private void afkCinematics$hands(CallbackInfo ci) {
        if (AfkCinematics.showing()) ci.cancel();
    }
}
