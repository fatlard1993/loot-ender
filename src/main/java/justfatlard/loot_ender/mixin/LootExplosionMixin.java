package justfatlard.loot_ender.mixin;

import java.util.List;

import justfatlard.loot_ender.LootGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Loot chests are taken out of a blast's list on the way into the clearing pass. See {@link LootGuard}. */
@Mixin(ServerExplosion.class)
public abstract class LootExplosionMixin {

	@Shadow @Final private ServerLevel level;

	@ModifyVariable(method = "interactWithBlocks", at = @At("HEAD"), argsOnly = true)
	private List<BlockPos> lootender$spareLootChests(List<BlockPos> positions) {
		if (positions.stream().noneMatch(pos -> LootGuard.spares(this.level, pos))) return positions;
		// Mutable: vanilla shuffles the list in place.
		return positions.stream().filter(pos -> !LootGuard.spares(this.level, pos))
			.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
	}
}
