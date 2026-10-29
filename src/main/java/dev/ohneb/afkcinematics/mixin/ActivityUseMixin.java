package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class ActivityUseMixin {
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$item(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (AfkCinematics.ownsCamera() && (!AfkCinematics.allowRodUse() || !player.getItemInHand(hand).is(Items.FISHING_ROD))) {
            // A cancelled main-hand use must not fall through to an unrelated offhand item.
            AfkCinematics.blockInput(((KeyMappingAccess)Minecraft.getInstance().options.keyUse).afkCinematics$key());
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
