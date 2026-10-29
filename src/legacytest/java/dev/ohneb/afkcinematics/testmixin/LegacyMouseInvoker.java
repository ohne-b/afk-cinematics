package dev.ohneb.afkcinematics.testmixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface LegacyMouseInvoker {
    @Invoker("onPress") void press(long window, int button, int action, int modifiers);
}
