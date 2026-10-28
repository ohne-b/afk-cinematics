package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.mixin.MusicManagerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class CinematicMusic {
    private static Track owned;
    private static int savedDelay;

    private CinematicMusic() { }

    public static boolean manages() {
        return owned != null && access().afkCinematics$current() == owned;
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        var access = access();
        boolean wanted = AfkCinematics.showing() && AfkCinematics.config().music;
        if (owned != null) {
            if (access.afkCinematics$current() != owned || owned.isStopped() || !client.getSoundManager().isActive(owned)) clear();
            else owned.fading = !wanted;
        }
        if (wanted && owned == null && access.afkCinematics$current() == null
            && client.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
            && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0) {
            savedDelay = access.afkCinematics$delay();
            owned = new Track();
            access.afkCinematics$current(owned);
            client.getSoundManager().play(owned);
        }
    }

    public static void clear() {
        if (owned == null) return;
        var client = Minecraft.getInstance();
        client.getSoundManager().stop(owned);
        var access = access();
        if (access.afkCinematics$current() == owned) {
            access.afkCinematics$current(null);
            access.afkCinematics$delay(Math.max(100, savedDelay));
        }
        owned = null;
    }

    private static MusicManagerAccess access() { return (MusicManagerAccess) Minecraft.getInstance().getMusicManager(); }

    private static final class Track extends AbstractTickableSoundInstance {
        boolean fading;

        Track() {
            super(SoundEvents.MUSIC_GAME.value(), SoundSource.MUSIC, RandomSource.create());
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.001F;
        }

        @Override public void tick() {
            volume = Math.clamp(volume + (fading ? -0.05F : 0.025F), 0, 1);
            if (fading && volume == 0) stop();
        }
    }
}
