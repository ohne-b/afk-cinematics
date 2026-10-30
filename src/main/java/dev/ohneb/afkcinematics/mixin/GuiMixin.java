package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    //? if >=26.2 {
    /*@Inject(method = "setScreen", at = @At("HEAD"))
    private void afkCinematics$screen(Screen screen, CallbackInfo ci) {
        AfkCinematics.screenChanged(screen);
    }
    *///?}
}
