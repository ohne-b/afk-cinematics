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
                if (System.getProperty("afkcinematics.smoke.world") != null) SceneProbe.start();
                else finish("Startup, mixin targets and settings render passed (" + loader + ").");
            }
        } catch (Exception exception) { throw new AssertionError("Startup smoke test failed", exception); }
    }

    static void finish(String message) throws Exception {
        Path result = Path.of(System.getProperty("afkcinematics.smoke.result"));
        Files.createDirectories(result.getParent());
        Files.writeString(result, message + "\n");
        Minecraft.getInstance().stop();
    }
}
