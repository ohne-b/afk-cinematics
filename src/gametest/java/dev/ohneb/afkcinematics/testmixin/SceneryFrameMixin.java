package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.TestSupport;
import dev.ohneb.afkcinematics.core.CinematicSession;
import dev.ohneb.afkcinematics.core.Pose;
import dev.ohneb.afkcinematics.core.Shot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CinematicSession.class, remap = false)
public abstract class SceneryFrameMixin {
    @Shadow private long lastFrame;
    @Shadow private Shot shot;
    @Unique private Shot afkCinematics$lastShot;
    @Unique private int afkCinematics$shotNumber;
    @Unique private Pose afkCinematics$previous;
    @Unique private double afkCinematics$opacity;
    @Unique private long afkCinematics$frameStarted;
    @Unique private long afkCinematics$tickStarted;
    @Unique private long afkCinematics$gap;

    @Inject(method = "frame", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$holdCoveredFrame(long now, CallbackInfoReturnable<Pose> cir) {
        if (TestSupport.cameraTimings != null) {
            afkCinematics$frameStarted = System.nanoTime();
            afkCinematics$gap = now - lastFrame;
        }
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
        if (session.settled() && shot != afkCinematics$lastShot) {
            afkCinematics$lastShot = shot;
            afkCinematics$shotNumber++;
            TestSupport.revealedShot = shot;
        }
        if (afkCinematics$previous != null && pose.position().distance(afkCinematics$previous.position()) > 2)
            TestSupport.require(afkCinematics$opacity == 1 && session.fade() == 1, "Scenery changed position during an uncovered frame");
        if (TestSupport.checkTransitions && afkCinematics$previous != null) {
            double movement = pose.position().distance(afkCinematics$previous.position());
            double turn = Math.abs(Math.IEEEremainder(pose.yaw() - afkCinematics$previous.yaw(), 360));
            if (afkCinematics$opacity == 1 && session.fade() == 1) {
                if (movement >= 0.3 || turn >= 5) TestSupport.coveredCuts++;
            } else {
                TestSupport.require(movement < 0.3, "Shot transition jumped position without two opaque frames");
                TestSupport.require(turn < 5, "Shot transition jumped rotation without two opaque frames");
            }
        }
        TestSupport.sceneryFrames.add(now + "," + pose.position().x() + "," + pose.position().y() + ","
            + pose.position().z() + "," + session.fade() + "," + session.scenery() + "," + afkCinematics$shotNumber
            + "," + (shot == null ? "" : shot.type()) + "," + pose.yaw() + "," + pose.pitch());
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
        if (TestSupport.cameraTimings != null && ((CinematicSession)(Object)this).ended())
            org.slf4j.LoggerFactory.getLogger("CameraValidation").info("Shot planning exhausted its candidates");
    }

    @Inject(method = "end", at = @At("HEAD"))
    private void afkCinematics$rejectedFrame(CallbackInfoReturnable<Pose> cir) {
        if (TestSupport.cameraTimings == null) return;
        var state = new StringBuilder("Frame gap: ").append(afkCinematics$gap / 1e6).append(" ms");
        for (String name : new String[]{"phase", "current", "currentFocus", "shot", "pending"}) {
            try {
                var field = CinematicSession.class.getDeclaredField(name);
                field.setAccessible(true);
                state.append("\n").append(name).append(": ").append(field.get(this));
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        org.slf4j.LoggerFactory.getLogger("CameraValidation").info("Rejected camera frame: {}", state);
    }
}
