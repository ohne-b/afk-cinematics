package dev.ohneb.afkcinematics.client;

import com.mojang.authlib.GameProfile;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.Difficulty;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class PlayerNamesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        boolean hidden = context.computeOnClient(TestSupport::hidden);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                floor(server.getPlayerList().getPlayers().getFirst());
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(20);
            context.runOnClient(client -> {
                hide(client, false);
                client.player.setYRot(0);
                client.player.setXRot(0);
                var other = new RemotePlayer(client.level, new GameProfile(UUID.randomUUID(), "CinematicGuest"));
                other.setId(3000001);
                other.setPos(client.player.getX(), client.player.getY(), client.player.getZ() + 3);
                other.setOldPosAndRot();
                client.level.addEntity(other);
                AfkCinematics.config().hideHud = false;
                AfkCinematics.config().hidePlayerNames = true;
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.STILL);
                playerNameFrames = 0;
            });
            context.waitFor(client -> playerNameFrames > 0);
            start(context);
            context.waitTicks(140);
            int hiddenFrames = context.computeOnClient(client -> playerNameFrames);
            context.waitTicks(10);
            context.runOnClient(client -> require(AfkCinematics.showing() && playerNameFrames == hiddenFrames,
                "A player label rendered during the cinematic"));
            var artifacts = Path.of(System.getProperty("afkcinematics.test.artifacts"));
            context.takeScreenshot(TestScreenshotOptions.of("player-names-hidden").withDestinationDir(artifacts));
            context.runOnClient(client -> AfkCinematics.config().hidePlayerNames = false);
            context.waitFor(client -> playerNameFrames > hiddenFrames);
            context.takeScreenshot(TestScreenshotOptions.of("player-names-shown").withDestinationDir(artifacts));
            context.runOnClient(client -> AfkCinematics.config().hidePlayerNames = true);
            context.waitTicks(5);
            int beforeStop = context.computeOnClient(client -> playerNameFrames);
            context.runOnClient(client -> AfkCinematics.stop());
            context.waitFor(client -> playerNameFrames > beforeStop);
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().hidePlayerNames = true;
                AfkCinematics.config().hideHud = true;
                hide(client, hidden);
                focused = null;
            });
        }
    }
}
