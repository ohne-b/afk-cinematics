package dev.ohneb.afkcinematics.smoke;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.SettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

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
        AfkCinematics.config().automatic = false;
        AfkCinematics.config().unfocused = true;
        Minecraft.getInstance().createWorldOpenFlows().openWorld(System.getProperty("afkcinematics.smoke.world"),
            () -> { throw new AssertionError("Scene fixture could not be opened"); });
    }

    static void tick(String loader) {
        if (System.nanoTime() - started > 120_000_000_000L) throw new AssertionError("Scene test timed out at stage " + stage);
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || AfkCinematics.screen() != null) return;
        if (stage == 0) {
            stage = 1;
            client.getSingleplayerServer().execute(() -> {
                var server = client.getSingleplayerServer();
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                var floor = player.blockPosition().below();
                for (BlockPos block : BlockPos.betweenClosed(floor.offset(-9, 0, -9), floor.offset(9, 6, 9)))
                    player.level().setBlockAndUpdate(block, (block.getY() == floor.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                player.teleportTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
                prepared = true;
            });
        } else if (stage == 1 && prepared && client.player.onGround() && ++ticks >= 40) {
            position = client.player.position();
            yaw = client.player.getYRot();
            pitch = client.player.getXRot();
            AfkCinematics.requestStart();
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && AfkCinematics.showing()) {
            stage = 3;
        } else if (stage == 3 && ++ticks >= 120) {
            if (!AfkCinematics.showing() || client.player.position().distanceToSqr(position) > 0.0001
                || client.player.getYRot() != yaw || client.player.getXRot() != pitch)
                throw new AssertionError("Cinematic did not preserve the player pose");
            AfkCinematics.showScreen(new SettingsScreen(null));
            if (AfkCinematics.ownsCamera()) throw new AssertionError("Settings did not restore camera control");
            try { SmokeProbe.finish("Startup, settings, six-second in-world camera and exit passed (" + loader + ")."); }
            catch (Exception exception) { throw new AssertionError(exception); }
        }
    }
}
