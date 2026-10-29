package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.mixin.KeyMappingAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ControlBindingsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                floor(player);
                supply(player);
            });
            context.waitFor(client -> client.player.onGround() && client.player.getInventory().countItem(Items.SNOWBALL) == 16);
            context.waitTicks(10);
            for (KeyMapping control : new KeyMapping[]{AfkCinematics.SETTINGS, AfkCinematics.TOGGLE}) {
                var oldUse = new AtomicReference<InputConstants.Key>();
                var oldControl = new AtomicReference<InputConstants.Key>();
                world.getServer().runOnServer(server -> supply(server.getPlayerList().getPlayers().getFirst()));
                context.waitFor(client -> client.player.getInventory().countItem(Items.SNOWBALL) == 16);
                context.runOnClient(client -> {
                    oldUse.set(((KeyMappingAccess)client.options.keyUse).afkCinematics$key());
                    oldControl.set(((KeyMappingAccess)control).afkCinematics$key());
                    control.setKey(keyboardKey(InputConstants.KEY_U));
                    client.options.keyUse.setKey(keyboardKey(InputConstants.KEY_U));
                    KeyMapping.resetMapping();
                    heldKey = InputConstants.KEY_U;
                });
                try {
                    key(context, InputConstants.KEY_U, InputConstants.PRESS);
                    if (control == AfkCinematics.TOGGLE) {
                        context.waitFor(client -> AfkCinematics.showing(), 120);
                        context.runOnClient(client -> AfkCinematics.showScreen(new InventoryScreen(client.player)));
                    } else context.runOnClient(client -> require(AfkCinematics.screen() instanceof SettingsScreen,
                        "Settings control did not open the screen"));
                    key(context, InputConstants.KEY_U, InputConstants.REPEAT);
                    key(context, InputConstants.KEY_ESCAPE, InputConstants.PRESS);
                    key(context, InputConstants.KEY_ESCAPE, InputConstants.RELEASE);
                    context.runOnClient(client -> {
                        require(AfkCinematics.screen() == null, "Control fixture did not close its screen");
                        KeyMapping.setAll();
                        KeyMapping.click(keyboardKey(InputConstants.KEY_U));
                        quietUse(client, "Closing a screen restored the held cinematic control as Use");
                    });
                    context.runOnClient(client -> focused = false);
                    key(context, InputConstants.KEY_U, InputConstants.RELEASE);
                    context.runOnClient(client -> focused = true);
                    context.waitTicks(2);
                    key(context, InputConstants.KEY_U, InputConstants.REPEAT);
                    context.runOnClient(client -> { KeyMapping.setAll(); quietUse(client, "Focus restoration forgot the custom hold"); });
                    if (control == AfkCinematics.TOGGLE) {
                        world.getServer().runOnServer(server -> {
                            var nether = server.getLevel(Level.NETHER);
                            for (BlockPos block : BlockPos.betweenClosed(new BlockPos(-9, 64, -9), new BlockPos(9, 70, 9)))
                                nether.setBlockAndUpdate(block, (block.getY() == 64 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                            server.getPlayerList().getPlayers().getFirst().teleportTo(nether, 0.5, 65, 0.5, Set.of(), 0, 0, true);
                        });
                        context.waitFor(client -> client.level.dimension() == Level.NETHER && AfkCinematics.screen() == null
                            && client.player.onGround(), 600);
                        context.runOnClient(client -> { KeyMapping.setAll(); quietUse(client, "Player replacement forgot the custom hold"); });
                    }
                    context.waitTicks(5);
                    world.getServer().runOnServer(server -> unchanged(server.getPlayerList().getPlayers().getFirst()));
                    context.runOnClient(client -> {
                        control.setKey(keyboardKey(InputConstants.KEY_K));
                        KeyMapping.resetMapping();
                        heldKey = -1;
                    });
                    key(context, InputConstants.KEY_U, InputConstants.RELEASE);
                    context.runOnClient(client -> {
                        require(!AfkCinematics.suppressed(keyboardKey(InputConstants.KEY_U)), "Rebinding stranded the old held input");
                        quietUse(client, "Use remained down after release");
                    });
                    key(context, InputConstants.KEY_U, InputConstants.PRESS);
                    key(context, InputConstants.KEY_U, InputConstants.RELEASE);
                    context.waitFor(client -> client.player.getInventory().countItem(Items.SNOWBALL) == 15, 60);
                    var serverCount = new AtomicInteger(-1);
                    for (int tick = 0; tick < 60 && serverCount.get() != 15; tick++) {
                        world.getServer().runOnServer(server -> serverCount.set(
                            server.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.SNOWBALL)));
                        context.waitTicks(1);
                    }
                    require(serverCount.get() == 15, "Fresh Use did not reach the server after release");
                    world.getServer().runOnServer(server -> {
                        var player = server.getPlayerList().getPlayers().getFirst();
                        player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(64)).forEach(Projectile::discard);
                    });
                } finally {
                    context.runOnClient(client -> { focused = true; heldKey = -1; });
                    key(context, InputConstants.KEY_U, InputConstants.RELEASE);
                    context.runOnClient(client -> {
                        AfkCinematics.stop();
                        control.setKey(oldControl.get());
                        client.options.keyUse.setKey(oldUse.get());
                        client.options.keyUse.setDown(false);
                        KeyMapping.resetMapping();
                    });
                }
            }

            var oldToggle = context.computeOnClient(client -> ((KeyMappingAccess)AfkCinematics.TOGGLE).afkCinematics$key());
            context.runOnClient(client -> {
                AfkCinematics.TOGGLE.setKey(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT));
                KeyMapping.resetMapping();
            });
            world.getServer().runOnServer(server -> supply(server.getPlayerList().getPlayers().getFirst()));
            try {
                mouse(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS);
                context.waitFor(client -> AfkCinematics.showing(), 120);
                context.runOnClient(client -> focused = false);
                mouse(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE);
                context.runOnClient(client -> focused = true);
                context.waitTicks(2);
                //? if <26.3 {
                context.runOnClient(client -> simulateControlClick = true);
                mouse(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS, 2);
                mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
                context.runOnClient(client -> quietUse(client, "Changed effective button escaped custom-control ownership"));
                mouse(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE);
                context.runOnClient(client -> require(AfkCinematics.suppressed(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT)),
                    "Custom-control release forgot the other physical owner"));
                mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
                //?} else {
                /*mouse(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE);
                *///?}
                context.runOnClient(client -> {
                    require(!AfkCinematics.suppressed(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT))
                        && !AfkCinematics.suppressed(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT)),
                        "Custom-control release retained mouse ownership");
                    quietUse(client, "Custom mouse control left Use down");
                });
                context.waitTicks(5);
                world.getServer().runOnServer(server -> unchanged(server.getPlayerList().getPlayers().getFirst()));
            } finally {
                context.runOnClient(client -> { focused = true; simulateControlClick = false; });
                mouse(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE);
                mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
                context.runOnClient(client -> { AfkCinematics.TOGGLE.setKey(oldToggle); KeyMapping.resetMapping(); });
            }
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                focused = null;
                heldKey = -1;
                simulateControlClick = false;
            });
        }
    }

    private static void quietUse(Minecraft client, String message) {
        require(!client.options.keyUse.isDown() && !client.options.keyUse.consumeClick(), message);
    }

    private static void supply(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.SNOWBALL, 16));
        player.inventoryMenu.broadcastFullState();
    }

    private static void unchanged(ServerPlayer player) {
        require(player.getInventory().countItem(Items.SNOWBALL) == 16, "Held cinematic control consumed a usable item");
        require(player.level().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(64)).isEmpty(),
            "Held cinematic control threw a projectile");
        require(player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).isEmpty(),
            "Held cinematic control dropped an item");
    }
}
