package dev.ohneb.afkcinematics.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ohneb.afkcinematics.client.HudVisibility;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class HudRenderMixin {
    //? if <26.2 {
    //? if >=26.1 {
    /*@WrapMethod(method = "extract")
    *///?} else {
    @WrapMethod(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V")
    //?}
    private void afkCinematics$hideModHuds(DeltaTracker delta, boolean advance, Operation<Void> original) {
        HudVisibility.render(() -> original.call(delta, advance));
    }
    //? if >=26.1 {
    /*@WrapMethod(method = "render()V")
    private void afkCinematics$renderModHuds(Operation<Void> original) {
        HudVisibility.render(() -> original.call());
    }
    *///?}
    //?}
}
