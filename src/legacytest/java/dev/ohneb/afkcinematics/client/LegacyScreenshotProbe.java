package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Shot;
import dev.ohneb.afkcinematics.mixin.KeyMappingAccess;
import dev.ohneb.afkcinematics.testmixin.LegacyKeyboardInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import java.util.EnumSet;

@SuppressWarnings("UnstableApiUsage")
final class LegacyScreenshotProbe {
    static void run(ClientGameTestContext context) {
        Class<?> capture;
        try { capture = Class.forName("me.ramidzkh.fabrishot.Fabrishot"); }
        catch (ClassNotFoundException absent) { return; }
        try {
            KeyMapping binding = (KeyMapping)capture.getField("SCREENSHOT_BINDING").get(null);
            var previous = ((KeyMappingAccess)binding).afkCinematics$key();
            try {
                context.runOnClient(client -> {
                    AfkCinematics.stop();
                    client.options.touchscreen().set(false);
                    AfkCinematics.config().subject = CinematicConfig.Subject.PLAYER;
                    AfkCinematics.config().shots = EnumSet.of(Shot.Type.DRIFT);
                    binding.setKey(InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_F9));
                    KeyMapping.resetMapping();
                    AfkCinematics.requestStart();
                });
                context.waitFor(client -> AfkCinematics.showing(), 240);
                context.waitTicks(140);
                var player = context.computeOnClient(client -> client.player.position());
                context.runOnClient(client -> {
                    require(client.gameRenderer.getMainCamera().getPosition().distanceTo(client.player.getEyePosition()) > 3,
                        "Legacy capture did not start from a cinematic view");
                    ((LegacyKeyboardInvoker)client.keyboardHandler).press(AfkCinematics.window(), InputConstants.KEY_F9, 0, InputConstants.PRESS, 0);
                    require(active(capture), "F9 did not start the older Fabrishot capture");
                    // Simulate a slow resize/readback before the next camera frame.
                    try { Thread.sleep(1_200); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
                    ((LegacyKeyboardInvoker)client.keyboardHandler).press(AfkCinematics.window(), InputConstants.KEY_F9, 0, InputConstants.RELEASE, 0);
                });
                context.waitFor(client -> !active(capture), 240);
                context.runOnClient(client -> {
                    require(AfkCinematics.showing(), "Slow older Fabrishot capture stopped the cinematic");
                    require(client.player.position().equals(player), "Older Fabrishot capture moved the player");
                });
                var camera = context.computeOnClient(client -> client.gameRenderer.getMainCamera().getPosition());
                context.waitFor(client -> client.gameRenderer.getMainCamera().getPosition().distanceTo(camera) > 0.001, 120);
                context.runOnClient(client -> require(AfkCinematics.showing(), "The older capture left the take frozen"));
            } finally {
                context.runOnClient(client -> {
                    binding.setKey(previous);
                    KeyMapping.resetMapping();
                });
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static boolean active(Class<?> capture) {
        try { return (boolean)capture.getMethod("isInCapture").invoke(null); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
