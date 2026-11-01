package dev.ohneb.afkcinematics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import dev.ohneb.afkcinematics.core.Letterbox;

public final class CinematicOverlay {
    private static final Letterbox BARS = new Letterbox();
    private static Object world, player, connection;
    private CinematicOverlay() { }

    public static void draw(GuiGraphics graphics) {
        var client = Minecraft.getInstance();
        if (world != client.level || player != client.player || connection != client.getConnection()
            || client.level == null || client.player == null || AfkCinematics.screen() != null && !AfkCinematics.chatting()) BARS.reset();
        world = client.level;
        player = client.player;
        connection = client.getConnection();
        if (world == null || player == null || AfkCinematics.screen() != null && !AfkCinematics.chatting()) return;
        double amount = BARS.sample(System.nanoTime(), AfkCinematics.showing() && AfkCinematics.config().letterbox,
            AfkCinematics.capturing());
        double fade = AfkCinematics.fadeOpacity();
        long cut = AfkCinematics.cameraCut();
        if (amount <= 0 && fade <= 0) return;
        //? if >=26.1 {
        /*graphics.nextStratum();
        *///?}
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        double barHeight = height * .09 * amount;
        int bar = (int)barHeight;
        graphics.fill(0, 0, width, bar, 0xFF000000);
        graphics.fill(0, height - bar, width, height, 0xFF000000);
        // Fractional edge coverage avoids whole-GUI-pixel jumps at larger GUI scales.
        int edge = (int)Math.round((barHeight - bar) * 255) << 24;
        if (edge != 0) {
            graphics.fill(0, bar, width, bar + 1, edge);
            graphics.fill(0, height - bar - 1, width, height - bar, edge);
        }
        if (fade > 0) {
            graphics.fill(0, 0, width, height, (int)Math.round(fade * 255) << 24);
            if (fade == 1) AfkCinematics.coveredFrame(cut);
        }
    }
}
