package dev.ohneb.afkcinematics.testmixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.ohneb.afkcinematics.client.TestSupport;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KeyMapping.class)
public abstract class PhysicalInputMixin {
    //? if >=26.3 {
    /*@Redirect(method = "setAll", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(I)Z"))
    private static boolean held(int key) {
        return key == TestSupport.heldKey || InputConstants.isKeyDown(key);
    }
    *///?} else {
    @Redirect(method = "setAll", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(Lcom/mojang/blaze3d/platform/Window;I)Z"))
    private static boolean held(Window window, int key) {
        return key == TestSupport.heldKey || InputConstants.isKeyDown(window, key);
    }
    //?}
}
