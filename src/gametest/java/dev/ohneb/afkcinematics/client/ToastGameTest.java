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
    public static Boolean rawHudHidden;
    public static boolean failRender;
    public static final class RenderFailure extends RuntimeException { }

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
                rawHudHidden = null;
                manager(client).addToast(toast);
            });
            context.waitFor(client -> toast.frames > 0);
            start(context);
            context.waitTicks(10);
            context.runOnClient(client -> {
                require(!hidden(client), "Cinematic changed the stored F1 setting");
                require(effectiveHidden(client), "Cinematic HUD hiding did not reach vanilla F1 consumers");
                //? if <26.2 {
                require(Boolean.TRUE.equals(rawHudHidden), "A third-party HUD still sees the raw F1 flag as visible");
                for (boolean next : new boolean[] {true, false}) {
                    HudVisibility.render(() -> {
                        require(client.options.hideGui, "Outer render did not hide mod HUDs");
                        HudVisibility.input(() -> {
                            require(client.options.hideGui != next, "Input read the render override");
                            client.options.hideGui = next;
                            HudVisibility.render(() -> require(client.options.hideGui, "Nested render lost hiding"));
                            require(client.options.hideGui == next, "Nested render discarded an F1 change");
                        });
                        require(client.options.hideGui, "Input did not restore the render override");
                    });
                    require(client.options.hideGui == next, "Outer render discarded the latest F1 state");
                }
                failRender = true;
                try {
                    //? if >=26.1 {
                    /*client.gameRenderer.extract(client.getDeltaTracker(), false);
                    *///?} else {
                    client.gameRenderer.render(client.getDeltaTracker(), false);
                    //?}
                    throw new AssertionError("Render failure probe did not run");
                } catch (RenderFailure expected) {
                    require(!hidden(client), "A failed render left the real F1 flag changed");
                } finally { failRender = false; }
                //?}
                //? if >=26.2 {
                /*require(client.gameRenderer.gameRenderState().guiRenderState.isHudHidden,
                    "Extracted HUD visibility remained stale");
                *///?}
            });
            int frames = context.computeOnClient(client -> toast.frames);
            int updates = context.computeOnClient(client -> toast.updates);
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(AfkCinematics.showing() && toast.frames == frames, "Toast rendered over a hidden cinematic HUD");
                require(toast.updates > updates, "Hiding toasts suspended their normal updates");
                AfkCinematics.config().hideHud = false;
            });
            context.waitFor(client -> toast.frames > frames);
            context.runOnClient(client -> {
                require(!effectiveHidden(client), "Disabling Hide HUD left vanilla visibility hidden");
                hide(client, true);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                require(AfkCinematics.showing() && hidden(client) && effectiveHidden(client), "F1 state was not preserved");
                AfkCinematics.config().hideHud = true;
                // Toggle twice under the override: each must invert the real stored state.
                //? if >=26.2 {
                /*client.gui.hud.toggle();
                require(!hidden(client) && effectiveHidden(client), "F1 toggle read the cinematic override");
                client.gui.hud.toggle();
                *///?}
                AfkCinematics.stop();
                require(hidden(client) && effectiveHidden(client), "Stopping lost the user's F1 setting");
                hide(client, false);
            });
            start(context);
            context.runOnClient(client -> AfkCinematics.config().hideHud = true);
            context.waitTicks(5);
            int beforeExit = context.computeOnClient(client -> toast.frames);
            context.runOnClient(client -> {
                //? if <26.1 {
                HudVisibility.render(() -> {
                    var keyboard = (dev.ohneb.afkcinematics.testmixin.KeyboardInvoker)client.keyboardHandler;
                    var f1 = new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_F1, 0, 0);
                    keyboard.press(AfkCinematics.window(), com.mojang.blaze3d.platform.InputConstants.PRESS, f1);
                    keyboard.press(AfkCinematics.window(), com.mojang.blaze3d.platform.InputConstants.RELEASE, f1);
                });
                require(hidden(client), "Reentrant F1 input was overwritten after waking");
                hide(client, false);
                //?}
                AfkCinematics.stop();
            });
            context.waitFor(client -> toast.frames > beforeExit);
        } finally {
            context.runOnClient(client -> {
                failRender = false;
                rawHudHidden = null;
                toast.show = false;
                AfkCinematics.stop();
                AfkCinematics.config().hideHud = true;
                hide(client, hidden);
                focused = null;
            });
        }
    }

    private static boolean effectiveHidden(Minecraft client) {
        //? if >=26.2 {
        /*return client.gui.hud.isHidden();
        *///?} else {
        return !Minecraft.renderNames();
        //?}
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
