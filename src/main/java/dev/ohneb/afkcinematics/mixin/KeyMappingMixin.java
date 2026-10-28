package dev.ohneb.afkcinematics.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Shadow private InputConstants.Key key;
    @Shadow private boolean isDown;
    @Shadow private int clickCount;

    @Inject(method = "setDown", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$restoredKey(boolean down, CallbackInfo ci) {
        if (AfkCinematics.suppressed(key)) {
            isDown = false;
            clickCount = 0;
            ci.cancel();
        }
    }

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void afkCinematics$click(InputConstants.Key key, CallbackInfo ci) {
        if (AfkCinematics.suppressed(key)) ci.cancel();
    }
}
