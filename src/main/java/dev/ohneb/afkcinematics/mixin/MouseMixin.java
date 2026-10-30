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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow private boolean ignoreFirstMove;
    @Shadow private double xpos;
    @Shadow private double ypos;
    //? if <1.21.9 {
    /*@Shadow private int fakeRightMouse;
    *///?}
    @Unique private final Map<Integer, Set<InputConstants.Key>> afkCinematics$physicalButtons = new HashMap<>();

    // Resolve the effective button before touchscreen filtering can discard a release.
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
    /*@Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$button(long window, int code, int action, int modifiers, CallbackInfo ci) {
        int physicalCode = code;
        // Legacy vanilla normalizes inline; inspect its state without changing its counter.
        if (Minecraft.ON_OSX && code == InputConstants.MOUSE_BUTTON_LEFT
            && (action == InputConstants.PRESS && (modifiers & 2) != 0 || action == InputConstants.RELEASE && fakeRightMouse > 0))
            code = InputConstants.MOUSE_BUTTON_RIGHT;
    *///?}
        if (window != AfkCinematics.window()) return;
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(code);
        Set<InputConstants.Key> owned = afkCinematics$physicalButtons.get(physicalCode);
        if (action == InputConstants.RELEASE) {
            if (owned != null) {
                if (!Minecraft.getInstance().isWindowActive()) {
                    // Cancel permitted activity use, but retain physical ownership until a trusted release.
                    for (InputConstants.Key released : owned) AfkCinematics.input(released, action, false);
                    ci.cancel();
                    return;
                }
                afkCinematics$physicalButtons.remove(physicalCode);
                for (InputConstants.Key released : owned) {
                    if (!afkCinematics$heldByAnotherButton(released)) AfkCinematics.input(released, action, false);
                }
            }
            if (!afkCinematics$heldByAnotherButton(key) && AfkCinematics.input(key, action, false)) ci.cancel();
            return;
        }
        if (owned != null && !owned.contains(key)) {
            // The new effective button must never inherit an old screenshot exemption.
            AfkCinematics.blockInput(key);
            owned.add(key);
            ci.cancel();
            return;
        }
        //? if >=26.2 {
        /*boolean screenshot = AfkCinematics.matches(Minecraft.getInstance().options.keyScreenshot, key);
        *///?} else {
        boolean screenshot = false;
        //?}
        boolean blocked = AfkCinematics.input(key, action, screenshot);
        if (action == InputConstants.PRESS && AfkCinematics.retainsInput(key))
            afkCinematics$physicalButtons.computeIfAbsent(physicalCode, ignored -> new HashSet<>()).add(key);
        if (blocked) ci.cancel();
    }

    @Unique private boolean afkCinematics$heldByAnotherButton(InputConstants.Key key) {
        return afkCinematics$physicalButtons.values().stream().anyMatch(keys -> keys.contains(key));
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
