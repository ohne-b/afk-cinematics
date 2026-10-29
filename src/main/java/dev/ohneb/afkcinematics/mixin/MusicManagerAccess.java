package dev.ohneb.afkcinematics.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MusicManager.class)
public interface MusicManagerAccess {
    @Accessor("currentMusic") SoundInstance afkCinematics$current();
    @Accessor("currentMusic") void afkCinematics$current(SoundInstance sound);
    @Accessor("nextSongDelay") int afkCinematics$delay();
    @Accessor("nextSongDelay") void afkCinematics$delay(int ticks);
}
