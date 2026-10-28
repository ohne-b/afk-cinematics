package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.mixin.KeyMappingAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class InputSafetyGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                floor(player);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.onGround() && client.player.getInventory().countItem(Items.EMERALD) == 32);
            context.waitTicks(20);
            var previousUse = new AtomicReference<InputConstants.Key>();
            context.runOnClient(client -> {
                previousUse.set(((KeyMappingAccess)client.options.keyUse).afkCinematics$key());
                client.options.keyUse.setKey(keyboardKey(InputConstants.KEY_U));
                KeyMapping.resetMapping();
            });
            try {
                start(context);
                context.runOnClient(client -> heldKey = InputConstants.KEY_U);
                key(context, InputConstants.KEY_U, InputConstants.PRESS);
                context.runOnClient(client -> AfkCinematics.showScreen(new InventoryScreen(client.player)));
                context.runOnClient(client -> {
                    AfkCinematics.showScreen(null);
                    KeyMapping.setAll();
                    KeyMapping.click(keyboardKey(InputConstants.KEY_U));
                    require(!client.options.keyUse.isDown(), "Focus restoration reactivated suppressed Use");
                    require(!client.options.keyUse.consumeClick(), "Suppressed Use retained a queued click");
                });
                context.runOnClient(client -> heldKey = -1);
                key(context, InputConstants.KEY_U, InputConstants.RELEASE);
                context.runOnClient(client -> require(!client.options.keyUse.isDown(), "Use stayed down after release"));
                key(context, InputConstants.KEY_U, InputConstants.PRESS);
                context.runOnClient(client -> require(client.options.keyUse.isDown(), "A new Use press was suppressed"));
                key(context, InputConstants.KEY_U, InputConstants.RELEASE);
            } finally {
                context.runOnClient(client -> {
                    heldKey = -1;
                    client.options.keyUse.setKey(previousUse.get());
                    client.options.keyUse.setDown(false);
                    KeyMapping.resetMapping();
                });
            }

            var previousScreenshot = new AtomicReference<InputConstants.Key>();
            context.runOnClient(client -> {
                previousScreenshot.set(((KeyMappingAccess)client.options.keyScreenshot).afkCinematics$key());
                client.options.keyScreenshot.setKey(keyboardKey(InputConstants.KEY_Q));
                KeyMapping.resetMapping();
            });
            try {
                start(context);
                key(context, InputConstants.KEY_Q, InputConstants.PRESS);
                context.waitTicks(2);
                context.runOnClient(client -> require(AfkCinematics.showing(), "Screenshot press stopped the cinematic"));
                key(context, InputConstants.KEY_Q, InputConstants.REPEAT);
                context.waitTicks(5);
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    require(player.getInventory().countItem(Items.EMERALD) == 32, "Shared Screenshot/Drop binding dropped an item");
                    require(player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(12)).isEmpty(),
                        "Screenshot repeat spawned a dropped item");
                });
                key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            } finally {
                context.runOnClient(client -> {
                    client.options.keyScreenshot.setKey(previousScreenshot.get());
                    KeyMapping.resetMapping();
                });
            }

            //? if >=26.2 {
            /*for (int button : new int[]{InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.MOUSE_BUTTON_4}) {
                var screenshotCount = new java.util.concurrent.atomic.AtomicLong();
                context.runOnClient(client -> {
                    screenshotCount.set(screenshots(client));
                    client.options.keyScreenshot.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
                    KeyMapping.resetMapping();
                });
                try {
                    start(context);
                    mouse(context, button, InputConstants.PRESS);
                    context.waitFor(client -> screenshots(client) > screenshotCount.get(), 120);
                    context.runOnClient(client -> {
                        require(AfkCinematics.showing(), "Mouse screenshot stopped the cinematic");
                        require(!client.options.keyAttack.isDown() && !client.options.keyAttack.consumeClick(),
                            "Mouse screenshot activated a shared Attack binding");
                        require(!client.options.keyUse.isDown() && !client.options.keyUse.consumeClick(),
                            "Mouse screenshot activated a shared Use binding");
                    });
                    mouse(context, button, InputConstants.RELEASE);
                    context.runOnClient(client -> AfkCinematics.stop());
                } finally {
                    context.runOnClient(client -> {
                        client.options.keyScreenshot.setKey(previousScreenshot.get());
                        KeyMapping.resetMapping();
                    });
                }
            }
            *///?}

            start(context);
            key(context, InputConstants.KEY_Q, InputConstants.PRESS);
            context.runOnClient(client -> focused = false);
            context.waitTicks(2);
            key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            context.runOnClient(client -> focused = true);
            context.waitTicks(2);
            key(context, InputConstants.KEY_Q, InputConstants.REPEAT);
            world.getServer().runOnServer(server -> {
                var nether = server.getLevel(Level.NETHER);
                for (BlockPos block : BlockPos.betweenClosed(new BlockPos(-9, 64, -9), new BlockPos(9, 70, 9)))
                    nether.setBlockAndUpdate(block, (block.getY() == 64 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                server.getPlayerList().getPlayers().getFirst().teleportTo(nether, 0.5, 65, 0.5, Set.of(), 0, 0, true);
            });
            context.waitFor(client -> client.level.dimension() == Level.NETHER && AfkCinematics.screen() == null
                && client.player.onGround(), 600);
            key(context, InputConstants.KEY_Q, InputConstants.REPEAT);
            context.waitTicks(10);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.getInventory().countItem(Items.EMERALD) == 32, "Focus or dimension replacement forgot the suppressed drop");
                require(player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(12)).isEmpty(),
                    "Suppressed hold spawned an item after replacement");
            });
            key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            key(context, InputConstants.KEY_Q, InputConstants.PRESS);
            key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            context.waitFor(client -> client.player.getInventory().countItem(Items.EMERALD) == 31);
            start(context);
        }
        context.runOnClient(client -> {
            require(!AfkCinematics.ownsCamera(), "Active disconnect retained a camera owner");
            focused = null;
        });
    }
}
