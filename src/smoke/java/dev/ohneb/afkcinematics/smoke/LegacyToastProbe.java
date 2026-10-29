package dev.ohneb.afkcinematics.smoke;

//? if <1.21.2 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.lwjgl.opengl.GL11;
import java.util.Arrays;

final class LegacyToastProbe {
    private static ToastComponent manager;
    private static int updates;

    static void tick() {
        var client = Minecraft.getInstance();
        if (manager == null) {
            manager = new ToastComponent(client);
            manager.addToast(new ProbeToast());
        }
        var graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
        graphics.enableScissor(1, 1, 16, 16);
        int[] before = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, before);
        try {
            manager.render(graphics);
            int[] after = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, after);
            if (!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) || !Arrays.equals(before, after))
                throw new AssertionError("Toast rendering changed the surrounding clip");
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }
    }

    static void verify() {
        if (updates == 0 || manager.getToast(ProbeToast.class, Toast.NO_TOKEN) != null)
            throw new AssertionError("Hidden legacy toast did not progress and expire");
    }

    private static final class ProbeToast implements Toast {
        @Override public Visibility render(GuiGraphics graphics, ToastComponent component, long visibleFor) {
            updates++;
            int[] clip = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, clip);
            if (!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) || clip[2] != 0 || clip[3] != 0)
                throw new AssertionError("Hidden legacy toast was not clipped");
            graphics.fill(0, 0, width(), height(), 0xFFFFFFFF);
            return Visibility.HIDE;
        }
    }
}
*///?}
