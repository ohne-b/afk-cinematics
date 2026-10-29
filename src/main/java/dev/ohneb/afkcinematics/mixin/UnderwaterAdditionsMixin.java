package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {
    "net.minecraft.client.resources.sounds.UnderwaterAmbientSoundHandler",
    //? if >=26.3 {
    /*"net.minecraft.client.resources.sounds.UnderLiquidSubSound"
    *///?} else {
    "net.minecraft.client.resources.sounds.UnderwaterAmbientSoundInstances$SubSound"
    //?}
})
abstract class UnderwaterAdditionsMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUnderWater()Z"))
    private boolean cameraWater(LocalPlayer player) {
        return AfkCinematics.audioUnderwater(player, player.isUnderWater());
    }
}
