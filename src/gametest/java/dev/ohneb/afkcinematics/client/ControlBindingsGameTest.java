package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.mixin.KeyMappingAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import dev.ohneb.afkcinematics.testmixin.KeyboardInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
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
            chat(context);
            for (KeyMapping control : new KeyMapping[]{AfkCinematics.SETTINGS, AfkCinematics.TOGGLE, AfkCinematics.NEXT}) {
                var oldUse = new AtomicReference<InputConstants.Key>();
                var oldControl = new AtomicReference<InputConstants.Key>();
                world.getServer().runOnServer(server -> supply(server.getPlayerList().getPlayers().getFirst()));
                context.waitFor(client -> client.player.getInventory().countItem(Items.SNOWBALL) == 16);
                if (control == AfkCinematics.NEXT) start(context);
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
                    if (control != AfkCinematics.SETTINGS) {
                        context.waitFor(client -> AfkCinematics.showing(), 120);
                        if (control == AfkCinematics.NEXT) context.runOnClient(client -> require(AfkCinematics.ownsCamera(),
                            "Next shot stopped the cinematic"));
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

    private static void chat(ClientGameTestContext context) {
        var oldChat = context.computeOnClient(client -> ((KeyMappingAccess)client.options.keyChat).afkCinematics$key());
        var oldUse = context.computeOnClient(client -> ((KeyMappingAccess)client.options.keyUse).afkCinematics$key());
        var oldCommand = context.computeOnClient(client -> ((KeyMappingAccess)client.options.keyCommand).afkCinematics$key());
        var oldToggle = context.computeOnClient(client -> ((KeyMappingAccess)AfkCinematics.TOGGLE).afkCinematics$key());
        context.runOnClient(client -> {
            AfkCinematics.config().keepChat = true;
            client.options.keyChat.setKey(keyboardKey(InputConstants.KEY_U));
            client.options.keyUse.setKey(keyboardKey(InputConstants.KEY_U));
            client.options.keyCommand.setKey(keyboardKey(InputConstants.KEY_SLASH));
            KeyMapping.resetMapping();
        });
        try {
            start(context);
            openingCharacter(context, InputConstants.KEY_U, 'u');
            context.waitFor(client -> AfkCinematics.screen() instanceof ChatScreen, 10);
            context.runOnClient(client -> {
                require(chatText().isEmpty(), "The chat opener was typed into its own field");
                quietUse(client, "Opening chat queued its shared Use binding");
                AfkCinematics.mouseMoved(10, 10);
                AfkCinematics.mouseScrolled();
            });
            key(context, InputConstants.KEY_U, InputConstants.RELEASE);
            key(context, InputConstants.KEY_K, InputConstants.PRESS);
            context.runOnClient(client -> character(client, 'k'));
            key(context, InputConstants.KEY_K, InputConstants.RELEASE);
            context.runOnClient(client -> heldKey = InputConstants.KEY_W);
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.runOnClient(client -> {
                require(chatText().equals("k"), "A cinematic key could not be typed in chat");
                require(AfkCinematics.showing(), "Chat input ended the view");
            });
            key(context, InputConstants.KEY_ESCAPE, InputConstants.PRESS);
            key(context, InputConstants.KEY_ESCAPE, InputConstants.RELEASE);
            key(context, InputConstants.KEY_W, InputConstants.REPEAT);
            context.runOnClient(client -> {
                KeyMapping.setAll();
                KeyMapping.click(keyboardKey(InputConstants.KEY_W));
                require(AfkCinematics.screen() == null && AfkCinematics.showing(), "Chat close or a held repeat ended the view");
                require(!client.options.keyUp.isDown() && !client.options.keyUp.consumeClick(), "Chat close restored a held movement key");
                heldKey = -1;
            });
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            openingCharacter(context, InputConstants.KEY_SLASH, '/');
            context.waitFor(client -> AfkCinematics.screen() instanceof ChatScreen, 10);
            context.runOnClient(client -> {
                require(chatText().equals("/"), "Command chat lost or duplicated its slash");
                hide(client, true);
            });
            context.takeScreenshot(TestScreenshotOptions.of("cinematic-chat-f1").withDestinationDir(
                java.nio.file.Path.of(System.getProperty("afkcinematics.test.artifacts"))));
            key(context, InputConstants.KEY_SLASH, InputConstants.RELEASE);
            context.runOnClient(client -> AfkCinematics.showScreen(new InventoryScreen(client.player)));
            context.runOnClient(client -> {
                require(!AfkCinematics.ownsCamera(), "Chat permission leaked into an inventory screen");
                AfkCinematics.showScreen(null);
                hide(client, false);
            });
            context.runOnClient(client -> {
                AfkCinematics.config().manualLook = true;
                AfkCinematics.requestStart();
                AfkCinematics.mouseMoved(2, 1);
            });
            context.waitFor(client -> AfkCinematics.showing(), 240);
            context.runOnClient(client -> {
                client.player.setYRot(client.player.getYRot() + 10);
                AfkCinematics.mouseMoved(2, 1);
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Manual look permission did not survive tick-based rotation checks");
                AfkCinematics.config().manualLook = false;
                AfkCinematics.mouseMoved(2, 1);
                require(!AfkCinematics.ownsCamera(), "Disabled manual look retained the camera");
            });
            start(context);
            context.runOnClient(client -> {
                AfkCinematics.input(keyboardKey(InputConstants.KEY_U), InputConstants.PRESS, false);
                AfkCinematics.stop();
            });
            context.waitTicks(2);
            context.runOnClient(client -> require(AfkCinematics.screen() == null, "A stale session opened chat"));
            key(context, InputConstants.KEY_U, InputConstants.RELEASE);
            start(context);
            context.runOnClient(client -> {
                AfkCinematics.TOGGLE.setKey(keyboardKey(InputConstants.KEY_U));
                KeyMapping.resetMapping();
            });
            key(context, InputConstants.KEY_U, InputConstants.PRESS);
            context.waitTicks(2);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera() && AfkCinematics.screen() == null,
                "A shared chat key overrode the cinematic toggle"));
            key(context, InputConstants.KEY_U, InputConstants.RELEASE);
        } finally {
            context.runOnClient(client -> {
                heldKey = -1;
                AfkCinematics.stop();
                AfkCinematics.showScreen(null);
                AfkCinematics.config().manualLook = false;
                client.options.keyChat.setKey(oldChat);
                client.options.keyUse.setKey(oldUse);
                client.options.keyCommand.setKey(oldCommand);
                AfkCinematics.TOGGLE.setKey(oldToggle);
                KeyMapping.resetMapping();
            });
        }
    }

    private static String chatText() {
        return AfkCinematics.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
            .findFirst().orElseThrow().getValue();
    }

    private static void openingCharacter(ClientGameTestContext context, int key, int character) {
        context.runOnClient(client -> {
            ((KeyboardInvoker)client.keyboardHandler).press(AfkCinematics.window(), InputConstants.PRESS, new KeyEvent(key, 0, 0));
            character(client, character);
        });
    }

    private static void character(Minecraft client, int character) {
        //? if >=26.3 {
        /*var event = new CharacterEvent(character);
        *///?} else {
        var event = new CharacterEvent(character, 0);
        //?}
        ((KeyboardInvoker)client.keyboardHandler).character(AfkCinematics.window(), event);
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
