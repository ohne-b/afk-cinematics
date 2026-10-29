package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.3 {
/*import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.function.Predicate;
*///?} else {
import org.spongepowered.asm.mixin.injection.Redirect;
//?}

//? if >=26.3 {
/*@Mixin(targets = "net.minecraft.client.resources.sounds.UnderLiquidAmbientSoundInstance")
*///?} else {
@Mixin(targets = "net.minecraft.client.resources.sounds.UnderwaterAmbientSoundInstances$UnderwaterAmbientSoundInstance")
//?}
abstract class UnderwaterLoopMixin {
    //? if >=26.3 {
    /*@ModifyArg(method = "underwater", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resources/sounds/UnderLiquidAmbientSoundInstance;<init>(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/client/player/LocalPlayer;Ljava/util/function/Predicate;)V"), index = 2)
    private static Predicate<LocalPlayer> cameraWater(Predicate<LocalPlayer> original) {
        return player -> AfkCinematics.audioUnderwater(player, original.test(player));
    }
    *///?} else {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUnderWater()Z"))
    private boolean cameraWater(LocalPlayer player) {
        return AfkCinematics.audioUnderwater(player, player.isUnderWater());
    }
    //?}
}
