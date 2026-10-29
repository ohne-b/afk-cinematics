package dev.ohneb.afkcinematics.client;

import dev.ohneb.afkcinematics.core.Pose;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;

import static dev.ohneb.afkcinematics.client.TestSupport.*;

@SuppressWarnings("UnstableApiUsage")
public final class ExternalCameraGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        configure(context);
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                floor(server.getPlayerList().getPlayers().getFirst());
            });
            context.waitFor(client -> client.player.onGround());
            context.waitTicks(20);
            var external = context.computeOnClient(client -> {
                var cameraEntity = new ArmorStand(EntityType.ARMOR_STAND, client.level);
                cameraEntity.setPos(client.player.position());
                cameraEntity.setOldPosAndRot();
                client.setCameraEntity(cameraEntity);
                return cameraEntity;
            });
            Pose player = context.computeOnClient(client -> new Pose(WorldGeometry.point(client.player.position()),
                client.player.getYRot(), client.player.getXRot()));
            try {
                start(context);
                context.waitTicks(70);
                context.runOnClient(client -> {
                    require(AfkCinematics.showing(), "An external camera prevented the cinematic");
                    require(client.getCameraEntity() == external, "The cinematic replaced the external camera entity");
                    require(camera(client).position().distanceTo(external.getEyePosition()) > 1,
                        "The cinematic did not move the rendered view");
                    require(WorldGeometry.point(client.player.position()).equals(player.position())
                        && client.player.getYRot() == player.yaw() && client.player.getXRot() == player.pitch(),
                        "An external camera cinematic changed the player pose");
                    AfkCinematics.stop();
                });
                context.waitTicks(2);
                context.runOnClient(client -> {
                    require(client.getCameraEntity() == external, "Stopping replaced the external camera entity");
                    require(camera(client).position().distanceTo(external.getEyePosition()) < 0.01,
                        "Stopping did not return to the external view");
                });
            } finally {
                context.runOnClient(client -> {
                    AfkCinematics.stop();
                    client.setCameraEntity(client.player);
                });
            }
        } finally {
            context.runOnClient(client -> focused = null);
        }
    }
}
