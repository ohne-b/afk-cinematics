package dev.ohneb.afkcinematics.testmixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import dev.ohneb.afkcinematics.client.TestSupport;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Collectors;

@Mixin(value = AfkCinematics.class, remap = false)
public abstract class CameraStopMixin {
    @Inject(method = "holdForScreenshot", at = @At("HEAD"), cancellable = true)
    private static void afkCinematics$continuousRecording(CallbackInfoReturnable<Runnable> cir) {
        // Video fixtures sample moving frames; ordinary screenshot tests retain capture holds.
        if (TestSupport.recordingFrames) cir.setReturnValue(() -> { });
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private static void afkCinematics$describeStop(CallbackInfo ci) {
        if (TestSupport.cameraTimings == null || !AfkCinematics.ownsCamera()) return;
        var client = Minecraft.getInstance();
        var player = client.player;
        TestSupport.cameraStop = "position=" + (player == null ? null : player.position())
            + ", onGround=" + (player != null && player.onGround()) + ", hurt=" + (player == null ? 0 : player.hurtTime)
            + ", paused=" + client.isPaused() + ", focus=" + client.isWindowActive() + ", screen=" + AfkCinematics.screen()
            + "\n" + StackWalker.getInstance().walk(frames -> frames.limit(8).map(Object::toString).collect(Collectors.joining("\n")));
        org.slf4j.LoggerFactory.getLogger("CameraValidation").info("Cinematic stopped: {}", TestSupport.cameraStop);
    }
}
