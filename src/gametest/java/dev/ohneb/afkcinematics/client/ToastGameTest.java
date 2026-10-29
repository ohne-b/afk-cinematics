package dev.ohneb.afkcinematics.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ToastGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        var toast = new ProbeToast();
        boolean hidden = context.computeOnClient(TestSupport::hidden);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> floor(server.getPlayerList().getPlayers().getFirst()));
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(20);
            context.runOnClient(client -> {
                hide(client, false);
                AfkCinematics.config().hideHud = true;
                manager(client).addToast(toast);
            });
            context.waitFor(client -> toast.frames > 0);
            start(context);
            context.waitTicks(10);
            int frames = context.computeOnClient(client -> toast.frames);
            int updates = context.computeOnClient(client -> toast.updates);
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(AfkCinematics.showing() && toast.frames == frames, "Toast rendered over a hidden cinematic HUD");
                require(toast.updates > updates, "Hiding toasts suspended their normal updates");
                AfkCinematics.config().hideHud = false;
            });
            context.waitFor(client -> toast.frames > frames);
            context.runOnClient(client -> AfkCinematics.config().hideHud = true);
            context.waitTicks(5);
            int beforeExit = context.computeOnClient(client -> toast.frames);
            context.runOnClient(client -> AfkCinematics.stop());
            context.waitFor(client -> toast.frames > beforeExit);
        } finally {
            context.runOnClient(client -> {
                toast.show = false;
                AfkCinematics.stop();
                AfkCinematics.config().hideHud = true;
                hide(client, hidden);
                focused = null;
            });
        }
    }

    private static ToastManager manager(Minecraft client) {
        //? if >=26.2 {
        /*return client.gui.toastManager();
        *///?} else {
        return client.getToastManager();
        //?}
    }

    private static final class ProbeToast implements Toast {
        int frames, updates;
        boolean show = true;
        public Visibility getWantedVisibility() { return show ? Visibility.SHOW : Visibility.HIDE; }
        public void update(ToastManager manager, long visibleFor) { updates++; }
        public void render(GuiGraphics graphics, Font font, long visibleFor) { frames++; }
    }
}
