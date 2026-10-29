package dev.ohneb.afkcinematics.testmixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseInvoker {
    @Invoker("onButton") void press(long window, MouseButtonInfo button, int action);
}
