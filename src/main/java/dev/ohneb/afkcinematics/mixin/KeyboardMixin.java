package dev.ohneb.afkcinematics.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.9 {
    private void afkCinematics$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        InputConstants.Key key = InputConstants.getKey(event);
        boolean screenshot = AfkCinematics.matches(Minecraft.getInstance().options.keyScreenshot, key);
    //?} else {
    /*private void afkCinematics$key(long window, int keyCode, int scan, int action, int modifiers, CallbackInfo ci) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scan);
        boolean screenshot = keyCode == InputConstants.KEY_F2;
    *///?}
        if (window == AfkCinematics.window()
            && AfkCinematics.input(key, action, screenshot || AfkCinematics.fabrishotKey(key))) ci.cancel();
    }
}
