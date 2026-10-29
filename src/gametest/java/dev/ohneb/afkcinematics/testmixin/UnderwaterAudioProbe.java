package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.TestSupport;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
abstract class UnderwaterAudioProbe {
    @Inject(method = "play", at = @At("HEAD"))
    private void played(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        if (!TestSupport.checkWaterAudio) return;
        String id = sound.getIdentifier().getPath();
        if (id.equals("ambient.underwater.loop") && sound instanceof TickableSoundInstance loop) {
            TestSupport.waterLoop = loop;
            TestSupport.waterLoops++;
        } else if (id.equals("ambient.underwater.enter") || id.equals("ambient.underwater.exit")) {
            TestSupport.require(sound.isRelative(), "Cinematic water transition was positioned at the player");
            TestSupport.waterTransitions++;
        }
    }
}
