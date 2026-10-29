package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
//? if >=26.2 {
/*import net.minecraft.world.entity.EntityTypes;
*///?} else {
import net.minecraft.world.entity.EntityType;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;
import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class TravelGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        context.runOnClient(client -> {
            AfkCinematics.config().boats = AfkCinematics.config().flight = AfkCinematics.config().travelControls = true;
            AfkCinematics.config().shots = EnumSet.of(Shot.Type.TRAIL);
        });
        try (var world = context.worldBuilder().create()) {
            var center = new AtomicReference<Vec3>();
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32));
                player.inventoryMenu.broadcastFullState();
                BlockPos base = player.blockPosition().below();
                center.set(new Vec3(base.getX() + .5, base.getY() + 3, base.getZ() + .5));
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-24, 0, -24), base.offset(24, 14, 24))) {
                    var material = block.getY() == base.getY() ? Blocks.SAND : block.getY() <= base.getY() + 2 ? Blocks.WATER : Blocks.AIR;
                    player.level().setBlock(block, material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                // Chest rafts use a different concrete class from ordinary boats.
                //? if >=26.2 {
                /*var boat = EntityTypes.BAMBOO_CHEST_RAFT.create(player.level(), EntitySpawnReason.COMMAND);
                *///?} else {
                var boat = EntityType.BAMBOO_CHEST_RAFT.create(player.level(), EntitySpawnReason.COMMAND);
                //?}
                require(boat != null, "Missing raft fixture");
                boat.snapTo(base.getX() + .5, base.getY() + 3, base.getZ() + .5, 0, 0);
                player.level().addFreshEntity(boat);
                require(player.startRiding(boat), "Could not board raft fixture");
            });
            context.waitFor(client -> client.player.isPassenger());
            context.waitTicks(40);
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            start(context);
            context.waitTicks(25);
            Vec3 before = context.computeOnClient(client -> client.player.position());
            Vec3 cameraBefore = context.computeOnClient(client -> camera(client).position());
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Steering stopped the manually started boat camera");
                require(client.player.position().distanceTo(before) > 2, "Boat controls did not reach vanilla");
                require(camera(client).position().distanceTo(cameraBefore) > 2, "Camera did not follow the raft");
                require(camera(client).position().distanceTo(client.player.position()) < 16, "Boat camera lost its subject");
            });
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            screenshot(context, "raft-trail");
            key(context, InputConstants.KEY_Q, InputConstants.PRESS);
            key(context, InputConstants.KEY_Q, InputConstants.REPEAT);
            key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            context.waitTicks(3);
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst()
                .getInventory().countItem(Items.EMERALD) == 32, "Travel wake dropped an item"));
            context.runOnClient(client -> {
                AfkCinematics.config().automatic = true;
                AfkCinematics.config().idleSeconds = 10;
            });
            context.waitFor(client -> AfkCinematics.showing(), 400);
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Automatic boat view accepted steering"));
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            context.runOnClient(client -> AfkCinematics.config().automatic = false);

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                // A different first passenger exercises the remotely controlled interpolation path.
                //? if >=26.2 {
                /*var boat = EntityTypes.OAK_BOAT.create(player.level(), EntitySpawnReason.COMMAND);
                var driver = EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
                *///?} else {
                var boat = EntityType.OAK_BOAT.create(player.level(), EntitySpawnReason.COMMAND);
                var driver = EntityType.PIG.create(player.level(), EntitySpawnReason.COMMAND);
                //?}
                require(boat != null && driver != null, "Missing passenger fixture");
                boat.snapTo(center.get());
                driver.snapTo(center.get());
                driver.setNoAi(true);
                player.level().addFreshEntity(boat);
                player.level().addFreshEntity(driver);
                require(driver.startRiding(boat) && player.startRiding(boat), "Could not create a second-passenger view");
                driverFirst(boat);
            });
            context.waitFor(client -> client.player.isPassenger() && client.player.getVehicle().getPassengers().size() == 2);
            context.runOnClient(client -> {
                driverFirst(client.player.getVehicle());
                require(client.player.getVehicle().getControllingPassenger() != client.player, "Fixture retained local steering");
            });
            context.runOnClient(client -> AfkCinematics.config().automatic = true);
            context.waitFor(client -> AfkCinematics.showing(), 400);
            context.waitTicks(20);
            for (int step = 0; step < 12; step++) {
                final int index = step;
                world.getServer().runOnServer(server -> {
                    var boat = server.getPlayerList().getPlayers().getFirst().getVehicle();
                    boat.setYRot(index < 6 ? 10 * index : 180 + 10 * index);
                    boat.setPos(boat.getX(), boat.getY(), boat.getZ() + (index < 6 ? .6 : -.6));
                });
                context.waitTicks(4);
                context.runOnClient(client -> require(AfkCinematics.showing(), "A remote driver's turn was treated as passenger input"));
            }
            context.runOnClient(client -> {
                try {
                    var field = AfkCinematics.class.getDeclaredField("course");
                    field.setAccessible(true);
                    require(Math.abs(Math.IEEEremainder(field.getDouble(null) - 180, 360)) < 20,
                        "Passenger plans kept their old course after the driver reversed");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                client.player.setYRot(client.player.getYRot() + 15);
            });
            context.waitFor(client -> !AfkCinematics.ownsCamera(), 5);
            context.runOnClient(client -> AfkCinematics.config().automatic = false);

            var air = new AtomicReference<Vec3>();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(player.getX(), player.getY() + 45, player.getZ());
                air.set(player.position());
            });
            context.waitFor(client -> !client.player.isPassenger() && client.player.getAbilities().flying
                && client.player.position().distanceToSqr(air.get()) < 1);
            context.waitTicks(10);
            context.runOnClient(client -> AfkCinematics.config().shots = EnumSet.of(Shot.Type.ESCORT));
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            start(context);
            context.waitTicks(30);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Flight input ended the manual camera");
                require(client.player.position().distanceTo(air.get()) > 4, "Flight input was swallowed");
                require(camera(client).position().distanceTo(client.player.position()) < 20, "Flight camera did not track the player");
            });
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            screenshot(context, "flight-escort");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            });
            context.waitFor(client -> !AfkCinematics.ownsCamera(), 20);
        } finally {
            context.runOnClient(client -> { AfkCinematics.stop(); AfkCinematics.config().automatic = false; focused = null; });
        }
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(Path.of(System.getProperty("afkcinematics.test.artifacts"))));
    }

    private static void driverFirst(net.minecraft.world.entity.Entity boat) {
        // Vanilla promotes players ahead of mobs. Keep the fixture's nonlocal driver in the first seat.
        if (!(boat.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player)) return;
        try {
            var passengers = net.minecraft.world.entity.Entity.class.getDeclaredField("passengers");
            passengers.setAccessible(true);
            passengers.set(boat, com.google.common.collect.ImmutableList.copyOf(boat.getPassengers().reversed()));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
}
