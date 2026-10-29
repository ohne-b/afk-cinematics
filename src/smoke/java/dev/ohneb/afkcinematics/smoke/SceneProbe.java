package dev.ohneb.afkcinematics.smoke;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.SettingsScreen;
import dev.ohneb.afkcinematics.client.WorldGeometry;
import dev.ohneb.afkcinematics.core.CinematicConfig;
import dev.ohneb.afkcinematics.core.Scene;
import dev.ohneb.afkcinematics.core.Shot;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/** Opens only a disposable fixture explicitly provided by the test runner. */
final class SceneProbe {
    private static long started;
    private static int stage;
    private static int ticks;
    private static volatile boolean prepared;
    private static Vec3 position;
    private static float yaw, pitch;

    private SceneProbe() { }
    static boolean started() { return started != 0; }

    static void start() {
        started = System.nanoTime();
        Minecraft.getInstance().options.pauseOnLostFocus = false;
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        AfkCinematics.config().enabled = true;
        AfkCinematics.config().automatic = false;
        AfkCinematics.config().unfocused = true;
        AfkCinematics.config().hideHud = true;
        AfkCinematics.config().subject = CinematicConfig.Subject.PLAYER;
        AfkCinematics.config().shots = EnumSet.of(Shot.Type.DRIFT);
        String fixture = System.getProperty("afkcinematics.smoke.world");
        if (fixture.equals("create")) {
            stage = -2;
            //? if >=1.21.9 {
            CreateWorldScreen.openFresh(Minecraft.getInstance(), () -> { throw new AssertionError("Fixture creation cancelled"); });
            //?} else {
            /*CreateWorldScreen.openFresh(Minecraft.getInstance(), AfkCinematics.screen());
            *///?}
        } else Minecraft.getInstance().createWorldOpenFlows().openWorld(fixture,
                () -> { throw new AssertionError("Scene fixture could not be opened"); });
    }

    static void tick(String loader) {
        if (System.nanoTime() - started > 120_000_000_000L) throw new AssertionError("Scene test timed out at stage " + stage);
        var client = Minecraft.getInstance();
        if (stage == -2 && AfkCinematics.screen() instanceof CreateWorldScreen screen) {
            stage = 0;
            var state = screen.getUiState();
            state.setName("AFK Cinematics validation");
            state.setSeed("20261029");
            state.setWorldType(state.getNormalPresetList().stream().filter(entry -> entry.preset().is(WorldPresets.FLAT))
                .findFirst().orElseThrow(() -> new AssertionError("Missing flat-world preset")));
            var create = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().equals(Component.translatable("selectWorld.create"))).findFirst()
                .orElseThrow(() -> new AssertionError("Missing create-world control"));
            //? if >=1.21.9 {
            create.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            //?} else {
            /*create.onPress();
            *///?}
            return;
        }
        if (client.player == null || client.level == null || AfkCinematics.screen() != null) return;
        if (stage == 0) {
            stage = 1;
            client.getSingleplayerServer().execute(() -> {
                var server = client.getSingleplayerServer();
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32));
                player.inventoryMenu.broadcastFullState();
                var floor = player.blockPosition().below();
                for (BlockPos block : BlockPos.betweenClosed(floor.offset(-9, 0, -9), floor.offset(9, 6, 9)))
                    player.level().setBlockAndUpdate(block, (block.getY() == floor.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                player.teleportTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
                prepared = true;
            });
        } else if (stage == 1 && prepared && client.player.onGround()
            && client.player.getInventory().countItem(Items.EMERALD) == 32 && ++ticks >= 40) {
            position = client.player.position();
            yaw = client.player.getYRot();
            pitch = client.player.getXRot();
            AfkCinematics.requestStart();
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && AfkCinematics.showing()) {
            stage = 3;
        } else if (stage == 3) {
            //? if <1.21.2 {
            /*LegacyToastProbe.tick();
            *///?}
            if (++ticks < 120) return;
            //? if <1.21.2 {
            /*LegacyToastProbe.verify();
            *///?}
            checkView(false);
            AfkCinematics.stop();
            prepared = false;
            stage = 4;
            client.getSingleplayerServer().execute(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                checkServer(player);
                BlockPos origin = player.blockPosition().below();
                for (BlockPos block : BlockPos.betweenClosed(origin.offset(-18, 1, -18), origin.offset(18, 8, 18))) {
                    if (block.getX() == origin.getX() && block.getZ() == origin.getZ() && block.getY() <= origin.getY() + 2) continue;
                    player.level().setBlock(block, (block.getY() == origin.getY() + 8 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                for (BlockPos block : BlockPos.betweenClosed(origin.offset(5, 8, 5), origin.offset(9, 8, 9)))
                    player.level().setBlockAndUpdate(block, Blocks.WATER.defaultBlockState());
                prepared = true;
            });
        } else if (stage == 4 && prepared && client.level.getBlockState(client.player.blockPosition().above(7)).is(Blocks.GRASS_BLOCK)) {
            AfkCinematics.config().subject = CinematicConfig.Subject.MIXED;
            AfkCinematics.requestStart();
            stage = 5;
            ticks = 0;
        } else if (stage == 5 && AfkCinematics.scenery() && AfkCinematics.fade() == 0) {
            stage = 6;
        } else if (stage == 6 && ++ticks >= 120) {
            checkView(true);
            prepared = false;
            stage = 7;
            client.getSingleplayerServer().execute(() -> {
                checkServer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                prepared = true;
            });
        } else if (stage == 7 && prepared) {
            AfkCinematics.showScreen(new SettingsScreen(null));
            if (AfkCinematics.ownsCamera()) throw new AssertionError("Settings did not restore camera control");
            try { SmokeProbe.finish("Startup, settings, player and scenery shots, unchanged server state and exit passed (" + loader + ")."); }
            catch (Exception exception) { throw new AssertionError(exception); }
        }
    }

    private static void checkView(boolean scenery) {
        var client = Minecraft.getInstance();
        //? if >=26.2 {
        /*Camera camera = client.gameRenderer.mainCamera();
        *///?} else {
        Camera camera = client.gameRenderer.getMainCamera();
        //?}
        //? if >1.21.10 {
        var point = WorldGeometry.point(camera.position());
        //?} else {
        /*var point = WorldGeometry.point(camera.getPosition());
        *///?}
        if (!AfkCinematics.showing() || AfkCinematics.scenery() != scenery || AfkCinematics.fade() != 0
            || client.player.position().distanceToSqr(position) > 0.0001
            || client.player.getYRot() != yaw || client.player.getXRot() != pitch)
            throw new AssertionError("Cinematic did not preserve the player pose");
        if (!Scene.inRange(point, WorldGeometry.point(position)) || !new WorldGeometry(client.level, AfkCinematics.underwater()).clear(point, point)
            || point.distance(WorldGeometry.point(client.player.getEyePosition())) < 1
            || scenery && point.y() < position.y() + 7)
            throw new AssertionError("Rendered camera did not reach a valid " + (scenery ? "scenery" : "player") + " view");
    }

    private static void checkServer(ServerPlayer player) {
        if (player.position().distanceToSqr(position) > 0.0001 || player.getInventory().countItem(Items.EMERALD) != 32
            || !player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(32)).isEmpty())
            throw new AssertionError("Cinematic changed server position, inventory or dropped items");
    }
}
