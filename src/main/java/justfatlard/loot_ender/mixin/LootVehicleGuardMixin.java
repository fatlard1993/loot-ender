package justfatlard.loot_ender.mixin;

import justfatlard.loot_ender.LootGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A chest minecart with loot aboard takes no harm but an op's. See {@link LootGuard}. */
@Mixin(VehicleEntity.class)
public abstract class LootVehicleGuardMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void lootender$spareLootVehicle(ServerLevel level, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		if (LootGuard.spares((VehicleEntity) (Object) this, source)) cir.setReturnValue(false);
	}
}
