package justfatlard.loot_ender.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The same for a chest minecart: see {@link LootUnpackMixin}. A hopper minecart under one was the same bypass. */
@Mixin(ContainerEntity.class)
public interface LootVehicleUnpackMixin {

	@Inject(method = "unpackChestVehicleLootTable", at = @At("HEAD"), cancellable = true, require = 1)
	private void lootender$keepTable(Player player, CallbackInfo ci) {
		ContainerEntity self = (ContainerEntity) this;
		if (!((Entity) this).level().isClientSide() && self.getContainerLootTable() != null) ci.cancel();
	}
}
