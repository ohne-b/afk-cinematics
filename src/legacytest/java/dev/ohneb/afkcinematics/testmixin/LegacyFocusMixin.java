package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.LegacyInputGameTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class LegacyFocusMixin {
    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void focus(CallbackInfoReturnable<Boolean> cir) {
        if (LegacyInputGameTest.focused != null) cir.setReturnValue(LegacyInputGameTest.focused);
    }
}
