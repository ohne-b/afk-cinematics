package dev.ohneb.afkcinematics.mixin;

import dev.ohneb.afkcinematics.client.AfkCinematics;
import net.minecraft.world.entity.Entity;
//? if >=1.21.11 {
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
//?} else if >=1.21.2 {
/*import net.minecraft.world.entity.vehicle.AbstractBoat;
*///?} else {
/*import net.minecraft.world.entity.vehicle.Boat;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.2 {
@Mixin(AbstractBoat.class)
//?} else {
/*@Mixin(Boat.class)
*///?}
public abstract class BoatMixin {
    @Unique private float afkCinematics$passengerYaw;

    @Inject(method = "positionRider", at = @At("HEAD"))
    private void afkCinematics$before(Entity passenger, Entity.MoveFunction movement, CallbackInfo ci) {
        afkCinematics$passengerYaw = passenger.getYRot();
    }

    @Inject(method = "positionRider", at = @At("RETURN"))
    private void afkCinematics$after(Entity passenger, Entity.MoveFunction movement, CallbackInfo ci) {
        // Observe vanilla's actual rotation/clamping; remote boat yaw alone does not imply a passenger turn.
        AfkCinematics.passengerTurn(passenger, passenger.getYRot() - afkCinematics$passengerYaw);
    }
}
