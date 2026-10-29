package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.TestSupport;
import dev.ohneb.afkcinematics.core.CinematicSession;
import dev.ohneb.afkcinematics.core.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CinematicSession.class, remap = false)
public abstract class SceneryFrameMixin {
    @Unique private Pose afkCinematics$previous;
    @Unique private double afkCinematics$opacity;

    @Inject(method = "frame", at = @At("HEAD"), cancellable = true)
    private void afkCinematics$holdCoveredFrame(long now, CallbackInfoReturnable<Pose> cir) {
        var session = (CinematicSession)(Object)this;
        if (TestSupport.holdCoveredScenery != null && session.fade() == 1
            && session.scenery() == TestSupport.holdCoveredScenery) cir.setReturnValue(session.current());
    }

    @Inject(method = "frame", at = @At("RETURN"))
    private void afkCinematics$checkCut(long now, CallbackInfoReturnable<Pose> cir) {
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
}
