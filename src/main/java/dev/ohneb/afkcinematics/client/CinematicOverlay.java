package dev.ohneb.afkcinematics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class CinematicOverlay {
    private CinematicOverlay() { }

    public static void draw(GuiGraphics graphics) {
        if (!AfkCinematics.showing()) return;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (AfkCinematics.config().letterbox) {
            int bar = Math.round(height * 0.09F);
            graphics.fill(0, 0, width, bar, 0xFF000000);
            graphics.fill(0, height - bar, width, height, 0xFF000000);
        }
        if (AfkCinematics.showHint()) graphics.drawCenteredString(Minecraft.getInstance().font,
            Component.translatable("ohneb_afk_cinematics.hint"), width / 2, height - Math.max(18, (int)(height * 0.09) + 12), 0xFFDDDDDD);
        int opacity = (int)Math.round(AfkCinematics.fade() * 255);
        if (opacity > 0) {
            graphics.fill(0, 0, width, height, opacity << 24);
            if (opacity == 255) AfkCinematics.coveredFrame();
        }
    }
}
