package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.CinematicOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
/*@Mixin(targets = "net.minecraft.client.gui.Hud")
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
public abstract class HudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$hide(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (AfkCinematics.hideHud()) {
            CinematicOverlay.draw(graphics);
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void afkCinematics$bars(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (!AfkCinematics.hideHud()) CinematicOverlay.draw(graphics);
    }
}
