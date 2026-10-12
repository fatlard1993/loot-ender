package justfatlard.loot_ender.mixin;

import justfatlard.loot_ender.LootOpening;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hoppers, hopper minecarts and droppers find no container at a loot chest.
 *
 * <p>With the table kept (see {@link LootUnpackMixin}) a hopper would read an empty chest and
 * push into it, and what it pushed would sit in the real chest where no player's copy shows it.
 * Either half of a double chest hides both.
 */
@Mixin(HopperBlockEntity.class)
public abstract class LootHopperMixin {

	@Inject(method = "getContainerAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;DDD)Lnet/minecraft/world/Container;",
		at = @At("RETURN"), cancellable = true, require = 1)
	private static void lootender$hideLoot(Level level, BlockPos pos, BlockState state, double x, double y, double z,
			CallbackInfoReturnable<Container> cir) {
		if (!(level instanceof ServerLevel serverLevel) || cir.getReturnValue() == null) return;
		if (cir.getReturnValue() instanceof ContainerEntity cart) {
			if (cart.getContainerLootTable() != null) cir.setReturnValue(null);
			return;
		}
		boolean loot = LootOpening.isLootContainer(serverLevel, pos)
			|| state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
				&& LootOpening.isLootContainer(serverLevel, ChestBlock.getConnectedBlockPos(pos, state));
		if (loot) cir.setReturnValue(null);
	}
}
