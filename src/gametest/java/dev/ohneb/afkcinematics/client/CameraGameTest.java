package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundSource;
import dev.ohneb.afkcinematics.mixin.MusicManagerAccess;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class CameraGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        context.runOnClient(client -> {
            client.options.pauseOnLostFocus = false;
            AfkCinematics.config().unfocused = true;
            AfkCinematics.config().automatic = false;
            client.options.setCameraType(CameraType.FIRST_PERSON);
        });
        try (var world = context.worldBuilder().create()) {
            var position = new AtomicReference<Vec3>();
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32));
                player.inventoryMenu.broadcastFullState();
                floor(player);
                position.set(player.position());
            });
            context.waitFor(client -> client.player.onGround() && !client.player.isCreative()
                && client.player.getInventory().countItem(Items.EMERALD) == 32);
            context.waitTicks(40);
            var rotation = new AtomicReference<Vec3>();
            context.runOnClient(client -> rotation.set(new Vec3(client.player.getYRot(), client.player.getXRot(), 0)));

            start(context);
            context.waitTicks(50);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Cinematic stopped without activity");
                require(camera(client).position().distanceTo(client.player.getEyePosition()) > 1,
                    "Camera did not leave the player's eyes");
                require(client.player.getYRot() == rotation.get().x && client.player.getXRot() == rotation.get().y,
                    "Cinematic rotated the player");
            });
            screenshot(context, "camera-open");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.position().distanceToSqr(position.get()) < 0.001, "Cinematic moved the server player");
                require(player.getInventory().countItem(Items.EMERALD) == 32, "Cinematic changed inventory");
            });

            key(context, InputConstants.KEY_Q, InputConstants.PRESS);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Drop input did not wake the camera"));
            key(context, InputConstants.KEY_Q, InputConstants.REPEAT);
            context.waitTicks(4);
            key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.getInventory().countItem(Items.EMERALD) == 32, "Wake input dropped an item");
                require(player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty(), "Wake input spawned a dropped item");
            });
            context.runOnClient(client -> require(client.options.getCameraType() == CameraType.FIRST_PERSON, "Perspective was not restored"));
            key(context, InputConstants.KEY_Q, InputConstants.PRESS);
            key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            context.waitFor(client -> client.player.getInventory().countItem(Items.EMERALD) == 31);

            start(context);
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.runOnClient(client -> {
                require(!AfkCinematics.ownsCamera(), "Movement did not wake the camera");
                require(client.options.keyUp.isDown(), "The waking movement press was lost");
            });
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            start(context);
            context.runOnClient(client -> AfkCinematics.showScreen(new InventoryScreen(client.player)));
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Opening inventory left the cinematic active"));
            context.runOnClient(client -> AfkCinematics.showScreen(null));

            start(context);
            context.waitTicks(40);
            var obstacle = new AtomicReference<BlockPos>();
            context.runOnClient(client -> obstacle.set(BlockPos.containing(camera(client).position())));
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().level()
                .setBlockAndUpdate(obstacle.get(), Blocks.STONE.defaultBlockState()));
            context.waitFor(client -> !AfkCinematics.ownsCamera());
            world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().level()
                .setBlockAndUpdate(obstacle.get(), Blocks.AIR.defaultBlockState()));
            context.waitFor(client -> client.level.getBlockState(obstacle.get()).isAir());

            context.runOnClient(client -> {
                AfkCinematics.config().motion = CinematicConfig.Motion.STILL;
                AfkCinematics.config().letterbox = true;
            });
            start(context);
            context.waitTicks(50);
            screenshot(context, "camera-letterbox");
            context.runOnClient(client -> AfkCinematics.openSettings());
            context.waitForScreen(SettingsScreen.class);
            screenshot(context, "settings");
            context.runOnClient(client -> {
                AfkCinematics.showScreen(null);
                AfkCinematics.config().automatic = true;
                AfkCinematics.config().idleSeconds = 10;
            });
            context.waitFor(client -> AfkCinematics.showing(), 400);
            context.runOnClient(client -> {
                AfkCinematics.stop();
                AfkCinematics.config().automatic = false;
            });

            context.runOnClient(client -> {
                focused = true;
                AfkCinematics.config().unfocused = false;
            });
            start(context);
            context.runOnClient(client -> focused = false);
            context.waitFor(client -> !AfkCinematics.ownsCamera());
            context.runOnClient(client -> AfkCinematics.config().unfocused = true);
            start(context);
            context.waitTicks(10);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Background option did not preserve the cinematic");
                AfkCinematics.mouseMoved(100, 100);
                require(AfkCinematics.showing(), "Unfocused mouse movement stopped the background camera");
                focused = true;
            });
            context.waitFor(client -> !AfkCinematics.ownsCamera());

            start(context);
            var reload = new AtomicReference<CompletableFuture<Void>>();
            context.runOnClient(client -> {
                reload.set(client.reloadResourcePacks());
                require(!AfkCinematics.ownsCamera(), "Resource reload did not release the camera");
            });
            //? if >=26.2 {
            /*context.waitFor(client -> reload.get().isDone() && client.gui.overlay() == null, 400);
            *///?} else {
            context.waitFor(client -> reload.get().isDone() && client.getOverlay() == null, 400);
            //?}

            context.runOnClient(client -> {
                client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.1);
                client.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.1);
                client.getMusicManager().stopPlaying();
                ((MusicManagerAccess)client.getMusicManager()).afkCinematics$delay(12000);
                AfkCinematics.config().music = true;
            });
            start(context);
            context.waitFor(client -> CinematicMusic.manages(), 80);
            context.runOnClient(client -> AfkCinematics.showScreen(new PauseScreen(true)));
            context.waitFor(client -> client.isPaused(), 40);
            context.waitFor(client -> !CinematicMusic.manages(), 80);
            context.runOnClient(client -> {
                require(((MusicManagerAccess)client.getMusicManager()).afkCinematics$current() == null,
                    "Paused cinematic retained its music instance");
                AfkCinematics.showScreen(null);
                AfkCinematics.config().music = false;
            });
        }
        context.runOnClient(client -> {
            require(!AfkCinematics.ownsCamera(), "Disconnect left a camera owner");
            focused = null;
        });
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.waitTicks(2);
        context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(Path.of(System.getProperty("afkcinematics.test.artifacts"))));
    }

}
