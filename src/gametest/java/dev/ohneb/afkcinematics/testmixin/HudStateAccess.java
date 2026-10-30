package dev.ohneb.afkcinematics.testmixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=26.2 {
/*@Mixin(targets = "net.minecraft.client.gui.Hud")
*///?} else {
@Mixin(net.minecraft.client.Options.class)
//?}
public interface HudStateAccess {
    //? if >=26.2 {
    /*@Accessor("isHidden")
    *///?} else {
    @Accessor("hideGui")
    //?}
    boolean afkCinematics$storedHidden();
}
