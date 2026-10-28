package dev.ohneb.afkcinematics.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.MouseHandler;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow private boolean ignoreFirstMove;
    @Shadow private double xpos;
    @Shadow private double ypos;

    //? if >=1.21.9 {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$button(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        int code = button.button();
    //?} else {
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$button(long window, int code, int action, int modifiers, CallbackInfo ci) {
    *///?}
        if (window == AfkCinematics.window() && AfkCinematics.input(InputConstants.Type.MOUSE.getOrCreate(code), action, false)) ci.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    //? if >=26.3 {
    /*private void afkCinematics$move(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
        if (window == AfkCinematics.window() && !ignoreFirstMove) AfkCinematics.mouseMoved(dx, dy);
    }
    *///?} else {
    private void afkCinematics$move(long window, double x, double y, CallbackInfo ci) {
        if (window == AfkCinematics.window() && !ignoreFirstMove) AfkCinematics.mouseMoved(x - xpos, y - ypos);
    }
    //?}

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void afkCinematics$scroll(long window, double x, double y, CallbackInfo ci) {
        if (window == AfkCinematics.window() && (x != 0 || y != 0)) AfkCinematics.stop();
    }
}
