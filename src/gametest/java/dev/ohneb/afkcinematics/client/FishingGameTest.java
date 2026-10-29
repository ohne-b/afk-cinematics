package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings({"UnstableApiUsage", "unchecked"})
public final class FishingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        context.runOnClient(client -> {
            AfkCinematics.config().fishing = true;
            AfkCinematics.config().automatic = true;
            AfkCinematics.config().shots = EnumSet.of(Shot.Type.ANGLER);
            sceneryFrames = new ArrayList<>();
            revealedShot = null;
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
                player.inventoryMenu.broadcastFullState();
                BlockPos base = player.blockPosition().below();
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-22, 0, -22), base.offset(22, 14, 22))) {
                    var material = block.getY() == base.getY() ? Blocks.SAND : block.getY() <= base.getY() + 5 ? Blocks.WATER : Blocks.AIR;
                    player.level().setBlock(block, material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-1, 5, -9), base.offset(1, 5, -7)))
                    player.level().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                player.setYRot(0);
                player.setXRot(20);
                player.teleportTo(base.getX() + .5, base.getY() + 6, base.getZ() - 7.5);
            });
            context.waitFor(client -> client.player.onGround() && client.player.getMainHandItem().is(Items.FISHING_ROD));
            context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(20); });
            context.waitTicks(15);
            click(context);
            context.waitFor(client -> FishingActivity.hook(client.player, client.level) != null, 100);
            context.waitFor(client -> revealedShot != null && revealedShot.type() == Shot.Type.ANGLER, 80);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Idle fishing did not start a view");
                AfkCinematics.mouseMoved(2, 1);
                require(AfkCinematics.showing(), "Looking around ended fishing");
                AfkCinematics.config().shots.add(Shot.Type.FLOAT);
            });
            screenshot(context, "fishing-angler");
            Shot waitingTake = context.computeOnClient(client -> revealedShot);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.fishing != null, "The cinematic retrieved the bobber");
                try {
                    var data = FishingHook.class.getDeclaredField("DATA_BITING");
                    data.setAccessible(true);
                    player.fishing.getEntityData().set((EntityDataAccessor<Boolean>)data.get(null), true);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            context.waitTicks(10);
            context.runOnClient(client -> require(revealedShot == waitingTake && AfkCinematics.fade() == 0,
                "A bite interrupted the current fishing take"));
            screenshot(context, "fishing-bite");
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst().fishing != null,
                "A bite triggered automatic reeling"));
            click(context);
            context.waitFor(client -> client.player.fishing == null && !AfkCinematics.ownsCamera(), 40);
            context.waitTicks(4);
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst().fishing == null,
                "The real reel action did not reach the server"));

            click(context);
            context.waitFor(client -> FishingActivity.hook(client.player, client.level) != null, 100);
            start(context);
            context.waitTicks(20);
            var use = context.computeOnClient(client -> ((dev.ohneb.afkcinematics.mixin.KeyMappingAccess)client.options.keyUse).afkCinematics$key());
            try {
                context.runOnClient(client -> {
                    client.options.keyUse.setKey(keyboardKey(InputConstants.KEY_W));
                    net.minecraft.client.KeyMapping.resetMapping();
                });
                key(context, InputConstants.KEY_W, InputConstants.PRESS);
                context.runOnClient(client -> require(!AfkCinematics.ownsCamera() && !client.options.keyUse.isDown()
                    && !client.options.keyUp.isDown(), "A shared movement key inherited rod-use permission"));
                key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            } finally {
                context.runOnClient(client -> { client.options.keyUse.setKey(use); net.minecraft.client.KeyMapping.resetMapping(); });
            }
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst().fishing != null,
                "The shared wake key reeled the bobber"));
            context.waitTicks(10);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Waking restarted the same cast automatically"));
            start(context);
            context.waitTicks(20);
            context.runOnClient(client -> {
                var mouse = (dev.ohneb.afkcinematics.testmixin.MouseInvoker)client.mouseHandler;
                var button = new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0);
                mouse.press(AfkCinematics.window(), button, InputConstants.PRESS);
                require(client.options.keyUse.isDown(), "The real rod press was not accepted");
                focused = false;
                mouse.press(AfkCinematics.window(), button, InputConstants.RELEASE);
                require(!AfkCinematics.ownsCamera() && !client.options.keyUse.isDown() && !client.options.keyUse.consumeClick(),
                    "An inactive rod release left use pressed or queued");
                var token = InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT);
                require(AfkCinematics.suppressed(token), "An inactive release lost rod suppression");
                focused = true;
                net.minecraft.client.KeyMapping.set(token, true);
                net.minecraft.client.KeyMapping.click(token);
                require(!client.options.keyUse.isDown() && !client.options.keyUse.consumeClick(),
                    "Restoring focus revived the held rod press");
                mouse.press(AfkCinematics.window(), button, InputConstants.RELEASE);
                require(!AfkCinematics.suppressed(token), "The active rod release retained suppression");
            });
            context.waitTicks(5);
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst().fishing != null,
                "Focus loss dispatched the queued rod press"));
            start(context);
            context.waitTicks(20);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 16));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FISHING_ROD));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.APPLE) && client.player.getOffhandItem().is(Items.FISHING_ROD));
            context.runOnClient(client -> require(AfkCinematics.showing(), "Changing rod hands ended the passive fishing view"));
            mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
            context.waitTicks(6);
            context.runOnClient(client -> {
                require(!AfkCinematics.ownsCamera(), "An unrelated main-hand action did not wake the view");
                require(!client.options.keyUse.isDown() && !client.player.isUsingItem(), "Wake use escaped suppression");
            });
            mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.getInventory().countItem(Items.APPLE) == 16 && player.fishing != null,
                    "An offhand rod authorized eating or reeling with the waking press");
            });
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().automatic = false;
                sceneryFrames = null;
                revealedShot = null;
                focused = null;
            });
        }
    }

    private static void click(ClientGameTestContext context) {
        mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
        context.waitTicks(1);
        mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(Path.of(System.getProperty("afkcinematics.test.artifacts"))));
    }
}
