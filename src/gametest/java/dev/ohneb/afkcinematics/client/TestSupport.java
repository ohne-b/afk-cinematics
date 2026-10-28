package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.testmixin.KeyboardInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("UnstableApiUsage")
public final class TestSupport {
    public static Boolean focused;
    public static int heldKey = -1;

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
            config.distance = CinematicConfig.Distance.NORMAL;
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
        context.waitFor(client -> AfkCinematics.showing(), 120);
    }

    public static void key(ClientGameTestContext context, int key, int action) {
        context.runOnClient(client -> ((KeyboardInvoker)client.keyboardHandler).press(AfkCinematics.window(), action, new KeyEvent(key, 0, 0)));
    }

    public static InputConstants.Key keyboardKey(int key) { return InputConstants.getKey(new KeyEvent(key, 0, 0)); }

    public static Camera camera(Minecraft client) {
        //? if >=26.1 {
        /*return client.gameRenderer.mainCamera();
        *///?} else {
        return client.gameRenderer.getMainCamera();
        //?}
    }

    public static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
