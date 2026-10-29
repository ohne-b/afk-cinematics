package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.TestSupport;
import dev.ohneb.afkcinematics.core.CinematicSession;
import dev.ohneb.afkcinematics.core.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CinematicSession.class, remap = false)
public abstract class SceneryFrameMixin {
    @Unique private Pose afkCinematics$previous;
    @Unique private double afkCinematics$opacity;
    @Unique private long afkCinematics$frameStarted;
    @Unique private long afkCinematics$tickStarted;

    @Inject(method = "frame", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$holdCoveredFrame(long now, CallbackInfoReturnable<Pose> cir) {
        if (TestSupport.cameraTimings != null) afkCinematics$frameStarted = System.nanoTime();
        var session = (CinematicSession)(Object)this;
        if (TestSupport.holdCoveredScenery != null && session.fade() == 1
            && session.scenery() == TestSupport.holdCoveredScenery) cir.setReturnValue(session.current());
    }

    @Inject(method = "frame", at = @At("RETURN"))
    private void afkCinematics$checkCut(long now, CallbackInfoReturnable<Pose> cir) {
        if (TestSupport.cameraTimings != null)
            TestSupport.cameraTimings.add("frame," + (System.nanoTime() - afkCinematics$frameStarted));
        Pose pose = cir.getReturnValue();
        if (TestSupport.sceneryFrames == null || pose == null) return;
        var session = (CinematicSession)(Object)this;
        if (afkCinematics$previous != null && pose.position().distance(afkCinematics$previous.position()) > 2)
            TestSupport.require(afkCinematics$opacity == 1 && session.fade() == 1, "Scenery changed position during an uncovered frame");
        TestSupport.sceneryFrames.add(now + "," + pose.position().x() + "," + pose.position().y() + ","
            + pose.position().z() + "," + session.fade() + "," + session.scenery());
        afkCinematics$previous = pose;
        afkCinematics$opacity = session.fade();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void afkCinematics$startTick(long now, CallbackInfo ci) {
        if (TestSupport.cameraTimings != null) afkCinematics$tickStarted = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void afkCinematics$finishTick(long now, CallbackInfo ci) {
        if (TestSupport.cameraTimings != null)
            TestSupport.cameraTimings.add("tick," + (System.nanoTime() - afkCinematics$tickStarted));
    }
}
