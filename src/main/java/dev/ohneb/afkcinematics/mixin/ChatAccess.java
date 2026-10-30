package dev.ohneb.afkcinematics.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.9 {
/*import org.spongepowered.asm.mixin.gen.Invoker;
*///?}

@Mixin(Minecraft.class)
public interface ChatAccess {
    //? if <1.21.9 {
    /*@Invoker("openChatScreen") void afkCinematics$chat(String initial);
    *///?}
}
