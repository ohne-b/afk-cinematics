package dev.ohneb.afkcinematics.mixin;

import com.mojang.blaze3d.platform.InputConstants;
//? if >=1.21.9 && <26.3 {
import com.mojang.blaze3d.platform.Window;
//?}
import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonInfo;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 && <26.3 {
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
//?}

import java.util.HashMap;
import java.util.Map;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow private boolean ignoreFirstMove;
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Unique private final Map<Integer, Integer> afkCinematics$physicalButtons = new HashMap<>();

    // Control-click must be normalized by vanilla before choosing the suppressed binding.
    //? if >=26.3 {
    /*@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$button(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        int code = button.button();
        int physicalCode = code;
    *///?} else if >=1.21.9 {
    @Inject(method = "onButton", at = @At(value = "INVOKE_ASSIGN",
        target = "Lnet/minecraft/client/MouseHandler;simulateRightClick(Lnet/minecraft/client/input/MouseButtonInfo;Z)Lnet/minecraft/client/input/MouseButtonInfo;"),
        locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void afkCinematics$button(long window, MouseButtonInfo raw, int action, CallbackInfo ci,
                                      Window gameWindow, boolean pressed, MouseButtonInfo button) {
        int code = button.button();
        int physicalCode = raw.button();
    //?} else {
    /*@Inject(method = "onPress", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;getOverlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0), cancellable = true)
    private void afkCinematics$button(long window, int code, int action, int modifiers, CallbackInfo ci) {
        int physicalCode = code;
    *///?}
        if (window != AfkCinematics.window()) return;
        code = afkCinematics$physicalButtons.getOrDefault(physicalCode, code);
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(code);
        if (action == InputConstants.RELEASE && (Minecraft.getInstance().isWindowActive() || !AfkCinematics.suppressed(key))) {
            afkCinematics$physicalButtons.remove(physicalCode);
            // Two physical buttons can share RIGHT; inactive releases must retain their association.
            if (afkCinematics$physicalButtons.containsValue(code)) { ci.cancel(); return; }
        }
        //? if >=26.2 {
        /*boolean screenshot = AfkCinematics.matches(Minecraft.getInstance().options.keyScreenshot, key);
        *///?} else {
        boolean screenshot = false;
        //?}
        boolean blocked = AfkCinematics.input(key, action, screenshot);
        if (action == InputConstants.PRESS && AfkCinematics.suppressed(key)) afkCinematics$physicalButtons.put(physicalCode, code);
        if (blocked) ci.cancel();
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
