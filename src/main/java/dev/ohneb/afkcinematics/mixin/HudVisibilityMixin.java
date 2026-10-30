package dev.ohneb.afkcinematics.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=26.2 {
/*@Mixin(targets = "net.minecraft.client.gui.Hud")
*///?} else {
@Mixin({net.minecraft.client.gui.Gui.class, net.minecraft.client.renderer.GameRenderer.class,
    net.minecraft.client.gui.components.DebugScreenOverlay.class})
//?}
public abstract class HudVisibilityMixin {
    // Preserve the stored F1 state. Modern layer predicates read the field directly;
    // the toggle must continue to invert its real value, not our transient override.
    //? if >=26.2 {
    /*@ModifyExpressionValue(method = "/\\A(isHidden|extractRenderState|lambda\\$registerVanillaLayers\\$.*)$/",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;isHidden:Z", opcode = 180))
    *///?} else {
    @ModifyExpressionValue(method = "*",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z", opcode = 180))
    //?}
    private static boolean afkCinematics$hidden(boolean original) {
        return original || AfkCinematics.hideHud();
    }
}
