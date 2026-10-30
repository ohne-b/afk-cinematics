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
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    //? if >=26.1 && <26.2 {
    /*@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "handleKeybinds")
    private void afkCinematics$realF1(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        dev.ohneb.afkcinematics.client.HudVisibility.input(() -> original.call());
    }
    *///?}
    //? if <26.2 {
    @ModifyExpressionValue(method = "renderNames",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z", opcode = 180))
    private static boolean afkCinematics$hiddenNames(boolean original) {
        return original || AfkCinematics.hideHud();
    }
    //?}

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$attack(CallbackInfoReturnable<Boolean> cir) {
        if (AfkCinematics.ownsCamera()) { AfkCinematics.stop(); cir.setReturnValue(false); }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$use(CallbackInfo ci) {
        if (AfkCinematics.ownsCamera() && !AfkCinematics.allowActivityUse()) {
            AfkCinematics.blockInput(((KeyMappingAccess)Minecraft.getInstance().options.keyUse).afkCinematics$key());
            ci.cancel();
        }
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
        AfkCinematics.screenChanged(screen);
    }
    //?}
}
