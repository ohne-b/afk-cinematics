package dev.ohneb.afkcinematics.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ohneb.afkcinematics.testmixin.LegacyMouseInvoker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

@SuppressWarnings("UnstableApiUsage")
public final class LegacyInputGameTest implements FabricClientGameTest {
    public static Boolean focused;

    @Override public void runTest(ClientGameTestContext context) {
        boolean touchscreen = context.computeOnClient(client -> client.options.touchscreen().get());
        context.runOnClient(client -> {
            focused = true;
            client.options.pauseOnLostFocus = false;
            AfkCinematics.config().enabled = true;
            AfkCinematics.config().automatic = false;
            AfkCinematics.config().unfocused = true;
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                server.setDifficulty(Difficulty.PEACEFUL, true);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                BlockPos floor = player.blockPosition().below();
                for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-9, 0, -9), floor.offset(9, 6, 9)))
                    player.level().setBlockAndUpdate(pos, (pos.getY() == floor.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                player.teleportTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 32));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.onGround() && client.player.getInventory().countItem(Items.EMERALD) == 32);
            context.waitTicks(20);
            context.runOnClient(client -> AfkCinematics.requestStart());
            context.waitFor(client -> AfkCinematics.showing(), 120);
            context.runOnClient(client -> client.options.touchscreen().set(true));
            button(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.PRESS);
            button(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.PRESS);
            button(context, InputConstants.MOUSE_BUTTON_LEFT, InputConstants.RELEASE);
            button(context, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.RELEASE);
            context.runOnClient(client -> {
                require(!AfkCinematics.suppressed(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT)),
                    "Touchscreen filtering lost the overlapping LEFT release");
                require(!client.options.keyAttack.isDown() && !client.options.keyAttack.consumeClick(), "Touchscreen release left Attack active");
                require(!client.options.keyUse.isDown(), "Touchscreen release left Use active");
                AfkCinematics.requestStart();
            });
            context.waitFor(client -> AfkCinematics.showing(), 120);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                require(player.getInventory().countItem(Items.EMERALD) == 32, "Mouse wake changed inventory");
                require(player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(12)).isEmpty(),
                    "Mouse wake dropped items");
            });
        } finally {
            context.runOnClient(client -> {
                AfkCinematics.stop();
                client.options.touchscreen().set(touchscreen);
                focused = null;
            });
        }
    }

    private static void button(ClientGameTestContext context, int button, int action) {
        context.runOnClient(client -> ((LegacyMouseInvoker)client.mouseHandler).press(AfkCinematics.window(), button, action, 0));
    }

    private static void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
