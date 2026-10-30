package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.mixin.MusicManagerAccess;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

public final class CinematicMusic {
    private static Track owned;
    private static int savedDelay;
    private static boolean skip;
    private static String previousDisc = "";
    private static final String[] DISCS = {"cat", "blocks", "chirp", "far", "mall", "mellohi", "stal", "strad", "ward", "wait", "otherside", "relic", "creator", "creator_music_box", "precipice"};
    private static final RandomSource RANDOM = RandomSource.create();

    private CinematicMusic() { }

    public static boolean manages() {
        return owned != null && access().afkCinematics$current() == owned;
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        var access = access();
        var config = AfkCinematics.config();
        boolean wanted = AfkCinematics.showing() && config.music && config.musicVolume > 0;
        if (owned != null) {
            // Paused sound engines do not advance tickable fades.
            if ((!wanted && client.isPaused()) || access.afkCinematics$current() != owned
                || owned.isStopped() || !client.getSoundManager().isActive(owned)) clear();
            else owned.fading = !wanted || skip || !owned.matches(config);
        }
        if (wanted && owned == null && access.afkCinematics$current() == null
            && client.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
            && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0) {
            SoundEvent sound = nextSound(config);
            if (sound == null) return;
            savedDelay = access.afkCinematics$delay();
            owned = new Track(sound, config);
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
        skip = false;
    }

    public static void next() { if (manages()) skip = true; }

    public static int enabled(boolean enabled) {
        AfkCinematics.config().music = enabled;
        boolean saved = AfkCinematics.store().save();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var message = net.minecraft.network.chat.Component.translatable(
                "ohneb_afk_cinematics." + (!saved ? "save_error" : enabled ? "music.on" : "music.off"));
            //? if >=26.1 {
            /*player.sendSystemMessage(message);
            *///?} else {
            player.displayClientMessage(message, false);
            //?}
        }
        return saved ? 1 : 0;
    }

    public static boolean customAvailable(String value) {
        var id = Identifier.tryParse(value);
        return id != null && Minecraft.getInstance().getSoundManager().getSoundEvent(id) != null;
    }

    private static SoundEvent nextSound(CinematicConfig config) {
        String name = switch (config.soundtrack) {
            case GAME -> "minecraft:music.game";
            case CREATIVE -> "minecraft:music.creative";
            case CUSTOM -> config.customMusic;
            case DISCS -> {
                var choices = java.util.Arrays.stream(DISCS).filter(disc -> !disc.equals(previousDisc)).toList();
                previousDisc = choices.get(RANDOM.nextInt(choices.size()));
                yield "minecraft:music_disc." + previousDisc;
            }
        };
        return customAvailable(name) ? SoundEvent.createVariableRangeEvent(Identifier.parse(name)) : null;
    }

    private static MusicManagerAccess access() { return (MusicManagerAccess) Minecraft.getInstance().getMusicManager(); }

    private static final class Track extends AbstractTickableSoundInstance {
        boolean fading;
        final CinematicConfig.Soundtrack soundtrack;
        final String custom;

        Track(SoundEvent sound, CinematicConfig config) {
            super(sound, SoundSource.MUSIC, RandomSource.create());
            soundtrack = config.soundtrack;
            custom = config.customMusic;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0.001F;
        }

        boolean matches(CinematicConfig config) {
            return soundtrack == config.soundtrack && (soundtrack != CinematicConfig.Soundtrack.CUSTOM || custom.equals(config.customMusic));
        }

        @Override public void tick() {
            float target = fading ? 0 : Math.clamp(AfkCinematics.config().musicVolume, 0, 100) / 100F;
            volume += Math.clamp(target - volume, -0.05F, 0.025F);
            if (fading && volume == 0) stop();
        }
    }
}
