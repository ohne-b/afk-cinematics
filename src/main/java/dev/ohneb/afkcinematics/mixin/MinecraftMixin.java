package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.CinematicMusic;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$attack(CallbackInfoReturnable<Boolean> cir) {
        if (AfkCinematics.ownsCamera()) { AfkCinematics.stop(); cir.setReturnValue(false); }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$use(CallbackInfo ci) {
        if (AfkCinematics.ownsCamera()) { AfkCinematics.stop(); ci.cancel(); }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$hold(boolean down, CallbackInfo ci) {
        if (down && AfkCinematics.ownsCamera()) { AfkCinematics.stop(); ci.cancel(); }
    }

    @Inject(method = "reloadResourcePacks", at = @At("HEAD"))
    private void afkCinematics$reload(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        AfkCinematics.stop();
        CinematicMusic.clear();
    }

    //? if <26.2 {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void afkCinematics$screen(net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        if (screen != null) AfkCinematics.stop();
    }
    //?}
}
