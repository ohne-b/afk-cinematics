package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.TestSupport;
import net.minecraft.client.MouseHandler;
//? if <26.3 {
import net.minecraft.client.input.InputQuirks;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class ControlClickMixin {
    //? if <26.3 {
    @Redirect(method = "simulateRightClick", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/input/InputQuirks;SIMULATE_RIGHT_CLICK_WITH_LONG_LEFT_CLICK:Z"))
    private boolean simulateControlClick() {
        return TestSupport.simulateControlClick || InputQuirks.SIMULATE_RIGHT_CLICK_WITH_LONG_LEFT_CLICK;
    }
    //?}
}
