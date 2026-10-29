package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
abstract class PlayerAudioMixin {
    @Unique private boolean afkCinematics$trackingAudio;
    @Unique private boolean afkCinematics$previousWater;
    @Unique private boolean afkCinematics$audioTransition;
    @Unique private SoundInstance afkCinematics$underwaterLoop;

    // Only the two sound-branch locals change. The real fluid field and return stay vanilla.
    @ModifyVariable(method = "updateIsUnderwater", at = @At("STORE"), index = 1)
    private boolean previousAudioWater(boolean normal) {
        afkCinematics$audioTransition = afkCinematics$trackingAudio
            || AfkCinematics.cameraAudioActive((LocalPlayer)(Object)this);
        return afkCinematics$trackingAudio ? afkCinematics$previousWater : normal;
    }

    @ModifyVariable(method = "updateIsUnderwater", at = @At("STORE"), index = 2)
    private boolean currentAudioWater(boolean normal) {
        var player = (LocalPlayer)(Object)this;
        boolean current = AfkCinematics.audioUnderwater(player, normal);
        afkCinematics$previousWater = current;
        afkCinematics$trackingAudio = AfkCinematics.cameraAudioActive(player);
        return current;
    }

    @ModifyArg(method = "updateIsUnderwater", at = @At(value = "INVOKE",
        //? if >=1.21.6 {
        target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"), index = 0)
        //?} else {
        /*target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"), index = 0)
        *///?}
    private SoundInstance replaceLoop(SoundInstance next) {
        // A fading loop can resume on quick cancellation; retire only this player's old loop.
        if (afkCinematics$audioTransition && afkCinematics$underwaterLoop != null)
            Minecraft.getInstance().getSoundManager().stop(afkCinematics$underwaterLoop);
        afkCinematics$underwaterLoop = next;
        return next;
    }

    @Redirect(method = "updateIsUnderwater", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void transitionSound(Level level, double x, double y, double z, SoundEvent sound,
                                 SoundSource category, float volume, float pitch, boolean delay) {
        if (afkCinematics$audioTransition)
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forAmbientAddition(sound));
        else level.playLocalSound(x, y, z, sound, category, volume, pitch, delay);
    }
}
