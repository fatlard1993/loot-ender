package justfatlard.loot_ender.mixin;

import justfatlard.loot_ender.LootOpening;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends a loot container's opener to their own copy instead of the block.
 *
 * <p>Deliberately here rather than on Fabric's UseBlockCallback. That event runs
 * before the block does and stops at the first handler that answers, so taking
 * it would decide, by mod load order, whether other mods watching for a chest
 * being opened ever hear about it. The village reputation penalty for opening a
 * village's chest is exactly such a watcher, and it should still fire: loot
 * being per player does not make taking it any less someone else's.
 */
@Mixin({ChestBlock.class, BarrelBlock.class})
public class LootContainerOpenMixin {

	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true, require = 1)
	private void lootender$openOwnCopy(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;

		InteractionResult result = LootOpening.tryOpen((ServerLevel) level, serverPlayer, state, pos);
		if (result != null) cir.setReturnValue(result);
	}
}
