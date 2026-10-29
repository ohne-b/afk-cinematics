package dev.ohneb.afkcinematics.testmixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface LegacyKeyboardInvoker {
    @Invoker("keyPress") void press(long window, int key, int scan, int action, int modifiers);
}
