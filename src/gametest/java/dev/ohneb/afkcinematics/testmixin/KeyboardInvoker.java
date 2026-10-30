package dev.ohneb.afkcinematics.testmixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardInvoker {
    @Invoker("keyPress") void press(long window, int action, KeyEvent event);
    @Invoker("charTyped") void character(long window, CharacterEvent event);
}
