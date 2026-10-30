package dev.ohneb.afkcinematics.client;

import net.minecraft.client.gui.GuiGraphics;

public final class CinematicOverlay {
    private CinematicOverlay() { }

    public static void draw(GuiGraphics graphics) {
        if (!AfkCinematics.showing()) return;
        //? if >=26.1 {
        /*graphics.nextStratum();
        *///?}
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (AfkCinematics.config().letterbox) {
            int bar = Math.round(height * 0.09F);
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }
        int opacity = (int)Math.round(AfkCinematics.fade() * 255);
        if (opacity > 0) {
            graphics.fill(0, 0, width, height, opacity << 24);
            if (opacity == 255) AfkCinematics.coveredFrame();
        }
    }
}
