package dev.ohneb.afkcinematics.mixin;

import com.mojang.blaze3d.platform.InputConstants;
//? if >=1.21.9 && <26.3 {
import com.mojang.blaze3d.platform.Window;
//?}
import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.MouseHandler;
//? if >=26.2 {
/*import net.minecraft.client.Minecraft;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 && <26.3 {
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
//?}

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow private boolean ignoreFirstMove;
    @Shadow private double xpos;
    @Shadow private double ypos;

    // Control-click must be normalized by vanilla before choosing the suppressed binding.
    //? if >=26.3 {
    /*@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$button(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        int code = button.button();
    *///?} else if >=1.21.9 {
    @Inject(method = "onButton", at = @At(value = "INVOKE_ASSIGN",
        target = "Lnet/minecraft/client/MouseHandler;simulateRightClick(Lnet/minecraft/client/input/MouseButtonInfo;Z)Lnet/minecraft/client/input/MouseButtonInfo;"),
        locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void afkCinematics$button(long window, MouseButtonInfo raw, int action, CallbackInfo ci,
                                      Window gameWindow, boolean pressed, MouseButtonInfo button) {
        int code = button.button();
    //?} else {
    /*@Inject(method = "onPress", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0), cancellable = true)
    private void afkCinematics$button(long window, int code, int action, int modifiers, CallbackInfo ci) {
    *///?}
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(code);
        //? if >=26.2 {
        /*boolean screenshot = AfkCinematics.matches(Minecraft.getInstance().options.keyScreenshot, key);
        *///?} else {
        boolean screenshot = false;
        //?}
        if (window == AfkCinematics.window() && AfkCinematics.input(key, action, screenshot)) ci.cancel();
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
