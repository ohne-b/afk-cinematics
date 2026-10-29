package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Shot;
import dev.ohneb.afkcinematics.mixin.KeyMappingAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Path;
import java.util.EnumSet;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ShotControlsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        var artifacts = Path.of(System.getProperty("afkcinematics.test.artifacts"));
        var oldNext = context.computeOnClient(client -> ((KeyMappingAccess)AfkCinematics.NEXT).afkCinematics$key());
        boolean oldHidden = context.computeOnClient(TestSupport::hidden);
        int oldDelay = AfkCinematics.config().idleSeconds;
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                floor(server.getPlayerList().getPlayers().getFirst());
            });
            context.waitFor(client -> client.player.onGround());
            context.runOnClient(client -> {
                hide(client, false);
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.PAN);
                AfkCinematics.config().pace = CinematicConfig.Pace.SHORT;
                AfkCinematics.NEXT.setKey(keyboardKey(InputConstants.KEY_N));
                KeyMapping.resetMapping();
            });
            Pose playerPose = context.computeOnClient(client -> new Pose(WorldGeometry.point(client.player.position()),
                client.player.getYRot(), client.player.getXRot()));
            start(context);
            context.waitTicks(140);
            double before = context.computeOnClient(client -> (double)camera(client).yRot());
            key(context, InputConstants.KEY_N, InputConstants.PRESS);
            key(context, InputConstants.KEY_N, InputConstants.RELEASE);
            context.waitTicks(40);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Next shot stopped the camera");
                require(Math.abs(Math.IEEEremainder(camera(client).yRot() - before, 360)) > 0.01,
                    "Next panorama did not turn");
            });
            context.waitTicks(65);
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.CRANE);
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos ceiling = player.blockPosition().above(2);
                for (BlockPos block : BlockPos.betweenClosed(ceiling.offset(-10, 0, -10), ceiling.offset(10, 0, 10)))
                    player.level().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            });
            context.waitFor(client -> client.level.getBlockState(client.player.blockPosition().above(2)).is(Blocks.STONE));
            context.waitTicks(5);
            context.runOnClient(client -> AfkCinematics.requestStart());
            context.waitFor(client -> AfkCinematics.ownsCamera());
            context.waitFor(client -> !AfkCinematics.ownsCamera(), 80);
            context.runOnClient(client -> require(message(client).equals(Component.translatable("ohneb_afk_cinematics.no_view").getString()),
                "An unavailable manual preview did not explain the missing view"));
            context.takeScreenshot(TestScreenshotOptions.of("no-clear-view").withDestinationDir(artifacts));
            context.runOnClient(client -> {
                //? if >=26.2 {
                /*client.gui.hud.setOverlayMessage(Component.empty(), false);
                *///?} else {
                client.gui.setOverlayMessage(Component.empty(), false);
                //?}
                AfkCinematics.config().automatic = true;
                AfkCinematics.config().idleSeconds = 10;
                AfkCinematics.stop();
            });
            context.waitTicks(245);
            context.runOnClient(client -> {
                require(message(client).isEmpty(), "An automatic attempt added a no-view notification");
                require(WorldGeometry.point(client.player.position()).distance(playerPose.position()) < 0.0001
                    && client.player.getYRot() == playerPose.yaw() && client.player.getXRot() == playerPose.pitch(),
                    "Shot controls changed the player pose");
            });
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().automatic = false;
                AfkCinematics.config().idleSeconds = oldDelay;
                AfkCinematics.NEXT.setKey(oldNext);
                KeyMapping.resetMapping();
                hide(client, oldHidden);
                focused = null;
            });
        }
    }

    private static String message(Minecraft client) {
        //? if >=26.2 {
        /*Object hud = client.gui.hud;
        *///?} else {
        Object hud = client.gui;
        //?}
        try {
            var field = hud.getClass().getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            var message = (Component)field.get(hud);
            return message == null ? "" : message.getString();
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
