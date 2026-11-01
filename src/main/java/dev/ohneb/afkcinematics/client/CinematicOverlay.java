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
        if (amount <= 0) return;
        //? if >=26.1 {
        /*graphics.nextStratum();
        *///?}
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        int bar = (int)Math.round(height * .09 * amount);
        graphics.fill(0, 0, width, bar, 0xFF000000);
        graphics.fill(0, height - bar, width, height, 0xFF000000);
    }
}
