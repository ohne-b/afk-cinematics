package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicReference;
import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class SleepGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        boolean keepChat = context.computeOnClient(client -> AfkCinematics.config().keepChat);
        var bed = new AtomicReference<BlockPos>();
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                floor(player);
                bed.set(player.blockPosition().south(2));
                var state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                    net.minecraft.resources.Identifier.parse("minecraft:red_bed")).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
                player.level().setBlockAndUpdate(bed.get().north(), state.setValue(BedBlock.PART, BedPart.FOOT));
                player.level().setBlockAndUpdate(bed.get(), state.setValue(BedBlock.PART, BedPart.HEAD));
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set night");
                server.overworld().getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 101, server);
            });
            context.waitFor(client -> client.player.onGround() && client.level.getBlockState(bed.get()).getBlock() instanceof BedBlock);
            world.getServer().runOnServer(server -> sleep(server.getPlayerList().getPlayers().getFirst(), bed.get()));
            context.waitFor(client -> AfkCinematics.screen() instanceof InBedChatScreen);
            context.waitTicks(10);
            context.runOnClient(client -> {
                require(!AfkCinematics.ownsCamera(), "Disabled sleep views started a camera");
                AfkCinematics.screen().onClose();
            });
            context.waitFor(client -> !client.player.isSleeping() && AfkCinematics.screen() == null);
            context.runOnClient(client -> {
                AfkCinematics.config().autoSleep = true;
                AfkCinematics.config().keepChat = false;
            });
            world.getServer().runOnServer(server -> sleep(server.getPlayerList().getPlayers().getFirst(), bed.get()));
            context.waitFor(client -> AfkCinematics.sleeping() && AfkCinematics.hideSleepOverlay(), 40);
            context.runOnClient(client -> {
                require(AfkCinematics.screen() instanceof InBedChatScreen, "Sleep replaced vanilla's bed controls");
                require(AfkCinematics.frameLimit() == 0, "Sleep applied the AFK frame cap");
                AfkCinematics.mouseMoved(10, 10);
                AfkCinematics.mouseScrolled();
                require(AfkCinematics.showing(), "Using the bed screen ended the camera");
            });
            var release = context.computeOnClient(client -> AfkCinematics.holdForScreenshot());
            key(context, InputConstants.KEY_F6, InputConstants.PRESS);
            key(context, InputConstants.KEY_F6, InputConstants.RELEASE);
            context.waitTicks(10);
            context.runOnClient(client -> {
                require(client.player.isSleeping() && !AfkCinematics.ownsCamera(), "Cancelling woke the player or restarted the same sleep");
                AfkCinematics.screen().onClose();
            });
            context.waitFor(client -> !client.player.isSleeping() && AfkCinematics.screen() == null);
            world.getServer().runOnServer(server -> sleep(server.getPlayerList().getPlayers().getFirst(), bed.get()));
            context.waitFor(client -> AfkCinematics.sleeping() && AfkCinematics.showing(), 40);
            context.runOnClient(client -> {
                release.run();
                heldKey = InputConstants.KEY_W;
            });
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.runOnClient(client -> {
                var field = AfkCinematics.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                field.setValue("sleep draft");
            });
            // The server wakes naturally after its ordinary sleep threshold.
            world.getServer().runOnServer(server -> server.overworld().getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 100, server));
            context.waitFor(client -> !client.player.isSleeping() && !AfkCinematics.ownsCamera(), 160);
            context.runOnClient(client -> {
                require(AfkCinematics.screen() instanceof ChatScreen, "Waking discarded the chat draft");
                var field = AfkCinematics.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                require(field.getValue().equals("sleep draft"), "Waking changed the chat draft");
                AfkCinematics.showScreen(null);
                KeyMapping.setAll();
                require(!client.options.keyUp.isDown(), "A bed-screen key leaked into movement after waking");
                heldKey = -1;
            });
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
        } finally {
            context.runOnClient(client -> {
                heldKey = -1;
                AfkCinematics.config().keepChat = keepChat;
                AfkCinematics.config().autoSleep = false;
                AfkCinematics.stop();
            });
        }
    }

    private static void sleep(ServerPlayer player, BlockPos bed) {
        // Bed interaction also registers the sleeper for vanilla's night-skip calculation.
        player.level().getBlockState(bed).useWithoutItem(player.level(), player,
            new BlockHitResult(Vec3.atCenterOf(bed), Direction.UP, bed, false));
        require(player.isSleeping(), "Vanilla bed interaction did not put the fixture player to sleep");
    }
}
