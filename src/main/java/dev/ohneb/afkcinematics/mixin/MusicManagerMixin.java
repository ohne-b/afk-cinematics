package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.CinematicMusic;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$ownedTrack(CallbackInfo ci) {
        if (CinematicMusic.manages()) ci.cancel();
    }
}
