package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//? if >=26.2 {
/*import net.minecraft.world.entity.EntityTypes;
*///?} else {
import net.minecraft.world.entity.EntityType;
//?}
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
*///?} else {
import net.minecraft.client.renderer.state.CameraRenderState;
//?}
//?} else {
/*import net.minecraft.client.renderer.entity.player.PlayerRenderer;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.9 {
@Mixin(AvatarRenderer.class)
//?} else {
/*@Mixin(PlayerRenderer.class)
*///?}
public abstract class PlayerNameMixin {
    //? if >=26.1 {
    /*@Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    *///?} else if >=1.21.9 {
    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    *///?}
    //? if >=1.21.9 {
    private void afkCinematics$names(AvatarRenderState state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera, CallbackInfo ci) {
        //? if >=26.2 {
        /*if (state.entityType == EntityTypes.PLAYER && AfkCinematics.hidePlayerNames()) ci.cancel();
        *///?} else {
        if (state.entityType == EntityType.PLAYER && AfkCinematics.hidePlayerNames()) ci.cancel();
        //?}
    }
    //?} else {
    /*private void afkCinematics$names(CallbackInfo ci) {
        if (AfkCinematics.hidePlayerNames()) ci.cancel();
    }
    *///?}
}
