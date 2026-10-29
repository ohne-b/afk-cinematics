package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Shot;
import dev.ohneb.afkcinematics.core.Vec;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;
import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class AirWaterGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        context.runOnClient(client -> {
            AfkCinematics.config().shots = EnumSet.of(Shot.Type.STILL);
            waterAudioHistory = new java.util.ArrayList<>();
        });
        try (var world = context.worldBuilder().create()) {
            var position = new AtomicReference<Vec3>();
            var floor = new AtomicReference<BlockPos>();
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                floor(player);
                floor.set(player.blockPosition().below());
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.getX(), player.getY() + 12, player.getZ());
                position.set(player.position());
            });
            context.waitFor(client -> client.player.getAbilities().flying && !client.player.onGround()
                && client.player.position().distanceToSqr(position.get()) < 0.01);
            context.waitTicks(20);
            world.getServer().runOnServer(server -> position.set(server.getPlayerList().getPlayers().getFirst().position()));
            start(context);
            context.waitTicks(140);
            context.runOnClient(client -> require(AfkCinematics.showing(), "Stationary flying player could not keep a cinematic"));
            context.takeScreenshot(TestScreenshotOptions.of("airborne-player-view").withDestinationDir(Path.of(System.getProperty("afkcinematics.test.artifacts"))));
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst()
                .position().distanceToSqr(position.get()) < 0.0001, "Cinematic moved the flying player"));
            context.runOnClient(client -> AfkCinematics.stop());

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos base = floor.get();
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-20, 0, -20), base.offset(20, 9, 20))) {
                    var material = block.getY() == base.getY() ? Blocks.SAND
                        : block.getY() < base.getY() + 8 ? Blocks.WATER : Blocks.AIR;
                    player.level().setBlock(block, material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
                position.set(player.position());
            });
            context.waitFor(client -> client.player.isUnderWater() && client.player.onGround());
            context.waitTicks(20);
            context.runOnClient(client -> {
                var geometry = new WorldGeometry(client.level);
                Vec wet = WorldGeometry.point(client.player.getEyePosition());
                Vec dry = wet.add(new Vec(0, 8, 0));
                var view = geometry.forScene(new Scene(wet, dry, Scene.Kind.PLAYER, 0));
                require(view.clear(wet, dry) && view.visible(dry, wet), "Player view cannot cross the water surface");
                require(!geometry.clear(wet, dry), "Dry scenery unexpectedly permits water");
                require(!new WorldGeometry(client.level, true).clear(wet, dry), "Submerged scenery can leave water");
                AmbientWaterChecks.verify(client);
            });
            start(context);
            context.waitTicks(140);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Submerged player could not keep a cinematic");
                require(camera(client).getFluidInCamera() == FogType.WATER, "Submerged player view has no water environment");
                AmbientWaterChecks.verify(client);
            });
            context.takeScreenshot(TestScreenshotOptions.of("submerged-player-view").withDestinationDir(Path.of(System.getProperty("afkcinematics.test.artifacts"))));
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst()
                .position().distanceToSqr(position.get()) < 0.01, "Cinematic moved the submerged player"));

            // A small externally caused water displacement is not a wake input.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.getX(), player.getY() + 0.08, player.getZ());
            });
            context.waitTicks(10);
            context.runOnClient(client -> require(AfkCinematics.showing(), "Passive water bobbing interrupted the take"));
            context.runOnClient(client -> {
                // Reproduce a wet player viewing a dry camera snapshot, then cancelling.
                // Keeping this in one client callback avoids a render replacing the snapshot.
                checkWaterAudio = true;
                waterLoops = 0;
                waterTransitions = 0;
                try {
                    var snapshot = AfkCinematics.class.getDeclaredField("cameraUnderwater");
                    snapshot.setAccessible(true);
                    snapshot.setBoolean(null, false);
                    var sounds = (dev.ohneb.afkcinematics.testmixin.PlayerWaterInvoker)client.player;
                    require(sounds.updateWater() && client.player.isUnderWater(), "Audio override changed real player fluid state");
                    require(!AfkCinematics.audioUnderwater(client.player, true), "Dry camera retained underwater audio");
                    AmbientWaterChecks.drySnapshot(client);
                    require(waterTransitions == 0, "Dry camera played a water exit splash");
                    AfkCinematics.stop();
                    require(sounds.updateWater() && client.player.isUnderWater(), "Restoring audio changed player fluid state");
                    AmbientWaterChecks.verify(client);
                    require(waterLoops == 1 && waterTransitions == 0, "Cancelling did not silently restore underwater ambience");
                    sounds.updateWater();
                    require(waterLoops == 1 && waterTransitions == 0, "Audio restoration repeated on the next update");
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                finally { checkWaterAudio = false; }
            });
            context.waitTicks(3);
            context.runOnClient(client -> require(waterAudioHistory.stream().filter(client.getSoundManager()::isActive).count() == 1,
                "Quick restoration left duplicate underwater loops playing"));
            start(context);
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Swimming input did not wake the cinematic"));
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            context.runOnClient(client -> {
                checkWaterAudio = true;
                waterTransitions = 0;
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(player.getX(), floor.get().getY() + 12, player.getZ());
            });
            context.waitFor(client -> !client.player.isUnderWater() && waterTransitions == 1);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(position.get().x, position.get().y, position.get().z);
            });
            context.waitFor(client -> client.player.isUnderWater() && waterTransitions == 2);
        } finally {
            context.runOnClient(client -> { AfkCinematics.stop(); focused = null; checkWaterAudio = false; waterLoop = null; waterAudioHistory = null; });
        }
    }
}
