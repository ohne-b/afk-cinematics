package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.CinematicSession;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.core.Shot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
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
            AfkCinematics.config().shots = EnumSet.of(Shot.Type.TRAIL);
        });
        boolean rotateWithCart = context.computeOnClient(client -> client.options.rotateWithMinecart().get());
        boolean gameFov = context.computeOnClient(client -> AfkCinematics.config().useGameFov);
        int customFov = context.computeOnClient(client -> AfkCinematics.config().customFov);
        context.runOnClient(client -> client.options.rotateWithMinecart().set(true));
        try (var world = context.worldBuilder().adjustSettings(settings -> {
            var previous = settings.getSettings();
            var data = previous.dataConfiguration();
            settings.setSettings(new net.minecraft.client.gui.screens.worldselection.WorldCreationContext(
                previous.options(), previous.datapackDimensions(), previous.selectedDimensions(), previous.worldgenRegistries(),
                previous.dataPackResources(), new net.minecraft.world.level.WorldDataConfiguration(data.dataPacks(),
                    data.enabledFeatures().join(net.minecraft.world.flag.FeatureFlagSet.of(net.minecraft.world.flag.FeatureFlags.MINECART_IMPROVEMENTS))),
                previous.initialWorldCreationOptions()));
        }).create()) {
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
            manual(context);
            context.waitTicks(25);
            Vec3 before = context.computeOnClient(client -> client.player.position());
            Vec3 cameraBefore = context.computeOnClient(client -> camera(client).position());
            context.waitTicks(20);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Steering stopped the manually started boat camera");
                require(AfkCinematics.frameLimit() == 0, "Boat view overrode normal FPS");
                require(client.player.position().distanceTo(before) > 2, "Boat controls did not reach vanilla");
                require(camera(client).position().distanceTo(cameraBefore) > 2, "Camera did not follow the raft");
                require(camera(client).position().distanceTo(client.player.position()) < 16, "Boat camera lost its subject");
            });
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            screenshot(context, "raft-trail");
            key(context, InputConstants.KEY_Q, InputConstants.PRESS);

            key(context, InputConstants.KEY_Q, InputConstants.RELEASE);
            context.waitTicks(3);
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst()
                .getInventory().countItem(Items.EMERALD) == 31, "Manual drop did not reach vanilla"));
            context.runOnClient(client -> {
                AfkCinematics.config().idleSeconds = 10;
            });
            start(context);
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.runOnClient(client -> require(!AfkCinematics.ownsCamera(), "Automatic boat view accepted steering"));
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);

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
            start(context);
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

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                BlockPos base = BlockPos.containing(center.get());
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-24, -1, -24), base.offset(24, 14, 24)))
                    player.level().setBlock(block, (block.getY() < base.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                    if (Math.abs(x) != 12 && Math.abs(z) != 12) continue;
                    boolean corner = Math.abs(x) == 12 && Math.abs(z) == 12;
                    RailShape shape = !corner ? (Math.abs(z) == 12 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH)
                        : x < 0 ? (z < 0 ? RailShape.SOUTH_EAST : RailShape.NORTH_EAST)
                        : z < 0 ? RailShape.SOUTH_WEST : RailShape.NORTH_WEST;
                    var rail = corner ? Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape)
                        : Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape).setValue(PoweredRailBlock.POWERED, true);
                    player.level().setBlock(base.offset(x, -1, z), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    player.level().setBlock(base.offset(x, 0, z), rail, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                for (int x : new int[]{-9, 9}) for (int z : new int[]{-9, 9}) {
                    for (BlockPos block : BlockPos.betweenClosed(base.offset(x - 2, 3, z - 2), base.offset(x + 2, 5, z + 2)))
                        player.level().setBlock(block, Blocks.OAK_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    for (int y = 0; y <= 4; y++) player.level().setBlock(base.offset(x, y, z), Blocks.OAK_LOG.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                //? if >=26.2 {
                /*var cart = EntityTypes.MINECART.create(player.level(), EntitySpawnReason.COMMAND);
                *///?} else {
                var cart = EntityType.MINECART.create(player.level(), EntitySpawnReason.COMMAND);
                //?}
                require(cart != null, "Missing minecart fixture");
                cart.snapTo(base.getX() + .5, base.getY() + .0625, base.getZ() - 11.5, 0, 0);
                player.level().addFreshEntity(cart);
                require(player.startRiding(cart), "Could not board minecart");
                cart.setDeltaMovement(.3, 0, 0);
            });
            context.waitFor(client -> client.player.getVehicle() != null && client.player.getVehicle().getType()
                //? if >=26.2 {
                /*== EntityTypes.MINECART);
                *///?} else {
                == EntityType.MINECART);
                //?}
            context.waitTicks(30);
            context.runOnClient(client -> require(client.level.enabledFeatures().contains(net.minecraft.world.flag.FeatureFlags.MINECART_IMPROVEMENTS),
                "Minecart rotation fixture did not enable experimental movement"));
            start(context);
            context.waitTicks(15);
            Vec3 cartBefore = context.computeOnClient(client -> client.player.getVehicle().position());
            Vec3 cartCameraBefore = context.computeOnClient(client -> camera(client).position());
            float riderYaw = context.computeOnClient(client -> client.player.getYRot());
            var turnedWithCart = new java.util.concurrent.atomic.AtomicBoolean();
            context.runOnClient(client -> cameraTimings = new java.util.ArrayList<>());
            for (int tick = 0; tick < 180; tick++) {
                context.waitTicks(1);
                context.runOnClient(client -> {
                    if (Math.abs(Math.IEEEremainder(client.player.getYRot() - riderYaw, 360)) > 30) turnedWithCart.set(true);
                    require(AfkCinematics.showing(), "Tree-lined rail corner ended the camera: " + cameraStop);
                });
            }
            context.runOnClient(client -> cameraTimings = null);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Rail travel or a turn stopped the automatic minecart view");
                require(turnedWithCart.get(), "The fixture did not rotate the rider through a rail corner");
                require(client.player.getVehicle().position().distanceTo(cartBefore) > 2, "Minecart did not travel on the rails");
                require(camera(client).position().distanceTo(cartCameraBefore) > 2, "Camera did not follow the minecart");
                require(AfkCinematics.frameLimit() == AfkCinematics.config().cinematicFps, "Automatic view lost the AFK cap");
                client.player.setYRot(client.player.getYRot() + 15);
            });
            context.waitFor(client -> !AfkCinematics.ownsCamera(), 5);
            manual(context);
            context.runOnClient(client -> AfkCinematics.mouseMoved(2, 1));
            context.runOnClient(client -> require(AfkCinematics.showing(), "Looking around ended a manual minecart view"));
            screenshot(context, "minecart-trail");
            context.runOnClient(client -> {
                client.player.setYRot(client.player.getYRot() + 15);
            });
            context.waitTicks(3);
            context.runOnClient(client -> require(AfkCinematics.showing(), "Manual rotation ended the camera"));
            key(context, InputConstants.KEY_LSHIFT, InputConstants.PRESS);
            context.waitFor(client -> !client.player.isPassenger(), 20);
            context.runOnClient(client -> require(AfkCinematics.ownsCamera(), "Dismount ended the manual session"));
            key(context, InputConstants.KEY_LSHIFT, InputConstants.RELEASE);
            world.getServer().runOnServer(server -> require(server.getPlayerList().getPlayers().getFirst()
                .getInventory().countItem(Items.EMERALD) == 31, "Minecart view changed inventory"));

            context.waitTicks(65); // Let vanilla's 60-tick dismount cooldown expire before the next fixture.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(!player.isShiftKeyDown(), "Server still sees Shift held before the tunnel fixture");
                BlockPos base = BlockPos.containing(center.get());
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-1, -1, -24), base.offset(1, 3, 24))) {
                    boolean wall = block.getX() != base.getX() || block.getY() == base.getY() + 3;
                    var material = block.getY() < base.getY() ? Blocks.REDSTONE_BLOCK : wall ? Blocks.STONE : Blocks.AIR;
                    player.level().setBlock(block, material.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                for (int z = -24; z <= 24; z++) player.level().setBlock(base.offset(0, 0, z),
                    Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH)
                        .setValue(PoweredRailBlock.POWERED, true), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                //? if >=26.2 {
                /*var cart = EntityTypes.MINECART.create(player.level(), EntitySpawnReason.COMMAND);
                *///?} else {
                var cart = EntityType.MINECART.create(player.level(), EntitySpawnReason.COMMAND);
                //?}
                require(cart != null, "Missing tunnel minecart");
                cart.snapTo(base.getX() + .5, base.getY() + .0625, base.getZ() - 13.5, 0, 0);
                player.level().addFreshEntity(cart);
                require(player.startRiding(cart), "Could not board tunnel minecart");
                cart.setDeltaMovement(0, 0, .2);
            });
            context.waitFor(client -> client.player.isPassenger());
            context.waitTicks(20);
            manual(context);
            Vec3 tunnelBefore = context.computeOnClient(client -> client.player.position());
            context.waitTicks(15);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "A straight rail tunnel had no trailing camera view");
                require(client.player.position().distanceTo(tunnelBefore) > 2, "Tunnel minecart did not move");
                require(Math.abs(camera(client).position().x() - center.get().x()) < .25, "Tunnel camera left the rail center");
                AfkCinematics.stop();
            });

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
            manual(context);
            context.waitTicks(30);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Flight input ended the manual camera");
                require(AfkCinematics.frameLimit() == 0, "Flight view overrode normal FPS");
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
            context.waitTicks(10);
            context.runOnClient(client -> { require(AfkCinematics.ownsCamera(), "Leaving ability flight ended manual mode"); AfkCinematics.stop(); });

            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FIREWORK_ROCKET, 8));
                player.teleportTo(player.getX(), player.getY() + 100, player.getZ());
                player.setOnGround(false);
                player.startFallFlying();
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.isFallFlying() && client.player.getMainHandItem().is(Items.FIREWORK_ROCKET));
            context.runOnClient(client -> { client.player.setXRot(-5); AfkCinematics.config().shots = EnumSet.of(Shot.Type.TRAIL); });
            manual(context);
            rocket(context);
            context.waitFor(client -> client.player.getMainHandItem().getCount() == 7);
            context.runOnClient(client -> require(AfkCinematics.showing() && client.player.isFallFlying(),
                "A genuine Elytra boost ended the manual flight view"));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.getMainHandItem().getCount() == 7, "The boost did not consume exactly one server rocket");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.FIREWORK_ROCKET, 5));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.getMainHandItem().isEmpty() && client.player.getOffhandItem().getCount() == 5);
            rocket(context);
            context.waitFor(client -> client.player.getOffhandItem().getCount() == 4);
            context.runOnClient(client -> require(AfkCinematics.showing(), "An offhand boost ended the manual flight view"));
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.getOffhandItem().getCount() == 4, "Offhand use did not reach vanilla");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 16));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(Items.APPLE));
            mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
            context.waitTicks(3);
            context.runOnClient(client -> require(AfkCinematics.ownsCamera() && client.options.keyUse.isDown(),
                "Manual mode blocked genuine item use"));
            mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
            context.runOnClient(client -> AfkCinematics.stop());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                player.stopFallFlying();
                player.teleportTo(center.get().x, center.get().y + 1, center.get().z);
                floor(player);
            });
            context.waitFor(client -> client.player.onGround() && !client.player.isFallFlying());
            manual(context);
            Object owner = context.computeOnClient(client -> cameraOwner());
            Vec3 walking = context.computeOnClient(client -> client.player.position());
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.waitTicks(12);
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            context.runOnClient(client -> require(cameraOwner() == owner && client.player.position().distanceTo(walking) > .5,
                "Manual view blocked walking or replaced its owner"));
            var walkingSession = (CinematicSession) owner;
            context.waitFor(client -> walkingSession.settled());
            int walkingTake = context.computeOnClient(client -> walkingSession.take());
            context.runOnClient(client -> AfkCinematics.nextShot());
            context.waitFor(client -> {
                require(cameraOwner() == owner && AfkCinematics.showing(), "Changing travel angles replaced the manual session");
                require(AfkCinematics.showing(), "Walking view stopped during a shot change");
                return walkingSession.take() > walkingTake;
            }, 140);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                //? if >=26.2 {
                /*var mount = EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
                *///?} else {
                var mount = EntityType.PIG.create(player.level(), EntitySpawnReason.COMMAND);
                //?}
                require(mount != null, "Missing animal fixture");
                mount.snapTo(player.position());
                mount.setNoAi(true);
                player.level().addFreshEntity(mount);
                require(player.startRiding(mount), "Could not mount the animal fixture");
            });
            context.waitFor(client -> client.player.isPassenger());
            context.waitTicks(30);
            context.runOnClient(client -> require(cameraOwner() == owner && AfkCinematics.showing(), "Mounting an animal replaced the manual session"));
            screenshot(context, "animal-follow");
            key(context, InputConstants.KEY_LSHIFT, InputConstants.PRESS);
            context.waitFor(client -> !client.player.isPassenger(), 20);
            key(context, InputConstants.KEY_LSHIFT, InputConstants.RELEASE);
            context.runOnClient(client -> require(cameraOwner() == owner, "Dismounting the animal replaced the session"));
            context.runOnClient(client -> AfkCinematics.stop());
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos base = player.blockPosition().below();
                for (BlockPos block : BlockPos.betweenClosed(base.offset(-20, 0, -20), base.offset(20, 12, 20)))
                    player.level().setBlock(block, (block.getY() == base.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                player.teleportTo(base.getX() + .5, base.getY() + 1, base.getZ() + .5);
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(25);
            context.runOnClient(client -> {
                client.player.setYRot(0);
                client.player.setXRot(0);
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.TRAIL);
                AfkCinematics.config().frontTravel = false;
                AfkCinematics.config().letterbox = true;
                AfkCinematics.config().useGameFov = false;
                AfkCinematics.config().customFov = 70;
            });
            manual(context);
            key(context, InputConstants.KEY_W, InputConstants.PRESS);
            context.waitTicks(12);
            context.runOnClient(client -> {
                AfkCinematics.config().shots = EnumSet.of(Shot.Type.PASS_BY);
                AfkCinematics.nextShot();
            });
            context.waitFor(client -> ((CinematicSession)cameraOwner()).type() == Shot.Type.PASS_BY, 35);
            context.waitTicks(2);
            Vec3 planted = context.computeOnClient(client -> camera(client).position());
            float plantedYaw = context.computeOnClient(client -> camera(client).yRot());
            Vec3 passer = context.computeOnClient(client -> client.player.position());
            context.waitTicks(8);
            context.runOnClient(client -> {
                require(AfkCinematics.showing(), "Roadside view ended while walking");
                require(camera(client).position().distanceTo(planted) < .001, "Roadside camera followed instead of staying planted");
                require(Math.abs(Math.IEEEremainder(camera(client).yRot() - plantedYaw, 360)) > 1, "Roadside camera did not pan");
                require(client.player.position().distanceTo(passer) > 1, "Roadside view blocked real movement");
                try {
                    var rigField = AfkCinematics.class.getDeclaredField("movingGeometry");
                    rigField.setAccessible(true);
                    Object rig = rigField.get(null);
                    var fovField = rig.getClass().getDeclaredField("fov");
                    fovField.setAccessible(true);
                    require(Math.abs(fovField.getDouble(rig) - 70) < .001,
                        "Roadside framing retained its initial FOV instead of the applied world lens");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            key(context, InputConstants.KEY_W, InputConstants.RELEASE);
            screenshot(context, "roadside-pass");
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                cameraTimings = null;
                client.options.rotateWithMinecart().set(rotateWithCart);
                AfkCinematics.config().useGameFov = gameFov;
                AfkCinematics.config().customFov = customFov;
                focused = null;
            });
        }
    }

    private static void rocket(ClientGameTestContext context) {
        mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
        context.waitTicks(1);
        mouse(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
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
