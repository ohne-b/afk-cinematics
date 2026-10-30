package dev.ohneb.afkcinematics.smoke;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.SettingsScreen;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;

final class SmokeProbe {
    private static int ticks;
    private static boolean opened;

    private SmokeProbe() { }

    static void tick(String loader) {
        if (SceneProbe.started()) { SceneProbe.tick(loader); return; }
        if (!Minecraft.getInstance().isGameLoadFinished()) return;
        if (AfkCinematics.screen() == null || ++ticks < 50) return;
        try {
            if (!opened) {
                for (String name : new String[]{"net.minecraft.client.Camera", "net.minecraft.client.KeyboardHandler",
                    "net.minecraft.client.MouseHandler", "net.minecraft.client.Options", "net.minecraft.client.sounds.MusicManager"})
                    Class.forName(name).getDeclaredMethods();
                AfkCinematics.showScreen(new SettingsScreen(null));
                opened = true;
                ticks = 0;
            } else {
                if (!(AfkCinematics.screen() instanceof SettingsScreen) || AfkCinematics.ownsCamera())
                    throw new AssertionError("Invalid settings or camera state");
                checkChoices();
                if (System.getProperty("afkcinematics.smoke.world") != null) SceneProbe.start();
                else finish("Startup, mixin targets and settings render passed (" + loader + ").");
            }
        } catch (Exception exception) { throw new AssertionError("Startup smoke test failed", exception); }
    }

    private static void checkChoices() {
        var config = AfkCinematics.config();
        var previous = config.soundtrack;
        try {
            config.soundtrack = dev.ohneb.afkcinematics.core.CinematicConfig.Soundtrack.CREATIVE;
            var screen = new SettingsScreen(null);
            AfkCinematics.showScreen(screen);
            press(screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).filter(button -> button.getMessage().getString().equals("Sound"))
                .findFirst().orElseThrow());
            press(playlist(screen));
            if (config.soundtrack != dev.ohneb.afkcinematics.core.CinematicConfig.Soundtrack.DISCS)
                throw new AssertionError("Playlist did not advance from its nonzero selection");
            press(playlist(screen));
            if (config.soundtrack != dev.ohneb.afkcinematics.core.CinematicConfig.Soundtrack.CUSTOM)
                throw new AssertionError("Rebuilding settings lost the playlist index");
            playlist(screen).mouseScrolled(0, 0, 0, 1);
            if (config.soundtrack != dev.ohneb.afkcinematics.core.CinematicConfig.Soundtrack.DISCS)
                throw new AssertionError("Playlist did not cycle backward");
        } finally {
            config.soundtrack = previous;
            AfkCinematics.showScreen(new SettingsScreen(null));
        }
    }

    private static net.minecraft.client.gui.components.CycleButton<?> playlist(SettingsScreen screen) {
        return screen.children().stream().filter(net.minecraft.client.gui.components.CycleButton.class::isInstance)
            .map(child -> (net.minecraft.client.gui.components.CycleButton<?>)child)
            .filter(button -> button.getValue() instanceof dev.ohneb.afkcinematics.core.CinematicConfig.Soundtrack)
            .findFirst().orElseThrow();
    }

    private static void press(net.minecraft.client.gui.components.AbstractButton button) {
        //? if >=1.21.9 {
        button.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_SPACE, 0, 0));
        //?} else {
        /*button.onPress();
        *///?}
    }

    static void finish(String message) throws Exception {
        Path result = Path.of(System.getProperty("afkcinematics.smoke.result"));
        Files.createDirectories(result.getParent());
        Files.writeString(result, message + "\n");
        Minecraft.getInstance().stop();
    }
}
