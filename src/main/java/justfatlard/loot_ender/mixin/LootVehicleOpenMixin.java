package justfatlard.loot_ender.mixin;

import justfatlard.loot_ender.LootOpening;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends the opener of a loot vehicle to their own copy, the way a chest in the ground does.
 *
 * <p>The block mixin next door cannot reach these: a cart is an entity, its loot table hangs off
 * the vehicle rather than a block entity, and vanilla unpacks it into the cart on the first look
 * rather than leaving it unrolled on a block.
 *
 * <p><b>On the interface, not on the minecart.</b> This used to inject into
 * {@code AbstractMinecartContainer.interact} and quietly never ran: {@code MinecartChest}
 * overrides {@code interact} and does not call {@code super}, so the parent's copy is dead code
 * for the one entity this was written for. Nothing said so - the injector's {@code require = 1}
 * was satisfied by the method existing on the parent, which proves a target was found and not
 * that anything reaches it.
 *
 * <p>{@code interactWithContainerVehicle} is where every container vehicle actually converges,
 * chest minecart and chest boat alike, and it is the method whose whole job is "open me".
 */
@Mixin(ContainerEntity.class)
public interface LootVehicleOpenMixin {

	@Inject(method = "interactWithContainerVehicle", at = @At("HEAD"), cancellable = true, require = 1)
	private void lootender$openOwnCopy(Player player, CallbackInfoReturnable<InteractionResult> cir) {
		ContainerEntity self = (ContainerEntity) this;
		Entity entity = (Entity) this;
		if (entity.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;

		InteractionResult result = LootOpening.tryOpenVehicle(
			(ServerLevel) entity.level(), serverPlayer, self, entity.getUUID(), entity.blockPosition());
		if (result != null) cir.setReturnValue(result);
	}
}
