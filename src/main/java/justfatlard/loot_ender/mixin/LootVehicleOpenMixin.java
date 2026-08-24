package justfatlard.loot_ender.mixin;

import justfatlard.loot_ender.LootOpening;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends the opener of a chest minecart to their own copy, the way a chest in the ground does.
 *
 * <p>The block mixin next door cannot reach these: a cart is an entity, its loot table hangs off
 * the vehicle rather than a block entity, and vanilla unpacks it into the cart on the first look
 * inside. A mineshaft was therefore first-come, first-served no matter what this mod did.
 *
 * <p>Injected at the head of the cart's own interact rather than on a Fabric event, for the same
 * reason the block one is: the event stops at the first handler that answers, and whether anybody
 * else watching for a container being opened hears about it should not depend on mod load order.
 */
@Mixin(AbstractMinecartContainer.class)
public abstract class LootVehicleOpenMixin {

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true, require = 1)
	private void lootender$openOwnCopy(Player player, InteractionHand hand, Vec3 hit,
			CallbackInfoReturnable<InteractionResult> cir) {
		AbstractMinecartContainer self = (AbstractMinecartContainer) (Object) this;
		if (self.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;

		InteractionResult result = LootOpening.tryOpenVehicle(
			(ServerLevel) self.level(), serverPlayer, self, self.getUUID(), self.blockPosition());
		if (result != null) cir.setReturnValue(result);
	}
}
