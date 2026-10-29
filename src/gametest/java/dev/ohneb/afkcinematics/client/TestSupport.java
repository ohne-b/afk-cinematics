package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Shot;
import dev.ohneb.afkcinematics.testmixin.KeyboardInvoker;
import dev.ohneb.afkcinematics.testmixin.MouseInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("UnstableApiUsage")
public final class TestSupport {
    public static Boolean focused;
    public static int heldKey = -1;
    public static boolean simulateControlClick;
    public static Boolean holdCoveredScenery;
    public static java.util.List<String> sceneryFrames;
    public static java.util.List<String> cameraTimings;
    public static String cameraStop;
    public static Shot revealedShot;
    public static boolean checkTransitions;
    public static int coveredCuts;
    public static int playerNameFrames;

    private TestSupport() { }

    public static void configure(ClientGameTestContext context) {
        context.runOnClient(client -> {
            focused = true;
            client.options.pauseOnLostFocus = false;
            var config = AfkCinematics.config();
            config.enabled = true;
            config.automatic = false;
            config.unfocused = true;
            config.music = false;
            config.motion = CinematicConfig.Motion.GENTLE;
            config.subject = CinematicConfig.Subject.PLAYER;
            config.pace = CinematicConfig.Pace.RELAXED;
            config.shots = java.util.EnumSet.allOf(Shot.Type.class);
        });
    }

    public static void floor(ServerPlayer player) {
        BlockPos floor = player.blockPosition().below();
        for (BlockPos block : BlockPos.betweenClosed(floor.offset(-9, 0, -9), floor.offset(9, 6, 9)))
            player.level().setBlockAndUpdate(block, (block.getY() == floor.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        player.teleportTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
    }

    public static void start(ClientGameTestContext context) {
        context.waitTicks(3);
        context.runOnClient(client -> AfkCinematics.requestStart());
        // Mixed mode may exhaust every player angle before finding its scenery fallback.
        context.waitFor(client -> AfkCinematics.showing(), 240);
    }

    public static void key(ClientGameTestContext context, int key, int action) {
        context.runOnClient(client -> ((KeyboardInvoker)client.keyboardHandler).press(AfkCinematics.window(), action, new KeyEvent(key, 0, 0)));
    }

    public static InputConstants.Key keyboardKey(int key) { return InputConstants.getKey(new KeyEvent(key, 0, 0)); }

    public static void mouse(ClientGameTestContext context, int button, int action) {
        mouse(context, button, action, 0);
    }

    public static void mouse(ClientGameTestContext context, int button, int action, int modifiers) {
        context.runOnClient(client -> ((MouseInvoker)client.mouseHandler).press(AfkCinematics.window(), new MouseButtonInfo(button, modifiers), action));
    }

    public static long screenshots(Minecraft client) {
        var path = client.gameDirectory.toPath().resolve("screenshots");
        if (!java.nio.file.Files.isDirectory(path)) return 0;
        try (var files = java.nio.file.Files.list(path)) { return files.filter(file -> file.toString().endsWith(".png")).count(); }
        catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    public static Camera camera(Minecraft client) {
        //? if >=26.2 {
        /*return client.gameRenderer.mainCamera();
        *///?} else {
        return client.gameRenderer.getMainCamera();
        //?}
    }

    public static boolean hidden(Minecraft client) {
        //? if >=26.2 {
        /*return client.gui.hud.isHidden();
        *///?} else {
        return client.options.hideGui;
        //?}
    }

    public static void hide(Minecraft client, boolean hidden) {
        //? if >=26.2 {
        /*if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
        *///?} else {
        client.options.hideGui = hidden;
        //?}
    }

    public static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
