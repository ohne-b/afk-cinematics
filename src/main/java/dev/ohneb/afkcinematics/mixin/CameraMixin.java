package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.core.Pose;
//? if >=1.21.11 {
import dev.ohneb.afkcinematics.core.Vec;
//?}
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
/*import net.minecraft.client.DeltaTracker;
*///?} else {
import net.minecraft.world.entity.Entity;
//? if >1.21.10 {
import net.minecraft.world.level.Level;
//?} else {
/*import net.minecraft.world.level.BlockGetter;
*///?}
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean detached;
    //? if >=1.21.11 {
    @Unique private Vec afkCinematics$lastPosition;
    //?}

    //? if >=26.1 {
    /*@Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void afkCinematics$camera(DeltaTracker delta, CallbackInfo ci) {
        boolean wasShowing = AfkCinematics.showing();
        if (!apply() && wasShowing && !AfkCinematics.showing()) ((Camera)(Object)this).update(delta);
    }
    *///?} else {
    @Inject(method = "setup", at = @At("RETURN"))
    //? if >1.21.10 {
    private void afkCinematics$camera(Level level, Entity entity, boolean detached, boolean mirrored, float delta, CallbackInfo ci) {
    //?} else {
    /*private void afkCinematics$camera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float delta, CallbackInfo ci) {
    *///?}
        boolean wasShowing = AfkCinematics.showing();
        if (!apply() && wasShowing && !AfkCinematics.showing()) {
            var type = Minecraft.getInstance().options.getCameraType();
            ((Camera)(Object)this).setup(level, entity, !type.isFirstPerson(), type.isMirrored(), delta);
        }
    }
    //?}

    private boolean apply() {
        Pose pose = AfkCinematics.cameraFrame((Camera)(Object)this);
        if (pose == null) {
            //? if >=1.21.11 {
            if (afkCinematics$lastPosition != null) refreshEnvironment();
            afkCinematics$lastPosition = null;
            //?}
            return false;
        }
        setPosition(pose.position().x(), pose.position().y(), pose.position().z());
        setRotation((float)pose.yaw(), (float)pose.pitch());
        //? if >=1.21.11 {
        if (AfkCinematics.fade() == 1 && !pose.position().equals(afkCinematics$lastPosition)) refreshEnvironment();
        afkCinematics$lastPosition = pose.position();
        //?}
        detached = Minecraft.getInstance().player.getEyePosition().distanceToSqr(
            pose.position().x(), pose.position().y(), pose.position().z()) >= 0.64;
        return true;
    }

    //? if >=1.21.11 {
    @Unique private void refreshEnvironment() {
        var client = Minecraft.getInstance();
        var camera = (Camera)(Object)this;
        if (client.level == null) return;
        camera.attributeProbe().reset();
        camera.attributeProbe().tick(client.level, camera.position());
    }
    //?}
}
