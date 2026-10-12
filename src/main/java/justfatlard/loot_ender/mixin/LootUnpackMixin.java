package justfatlard.loot_ender.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A loot chest's table is never rolled into the chest itself.
 *
 * <p>Vanilla rolls it the first time anything reads the contents, a hopper or a comparator as
 * readily as a hand, and clears the table as it does. A cleared table is an ordinary chest: no
 * lock, no copies, the lot for whoever wired a hopper under it. Every player gets a copy rolled
 * by {@link justfatlard.loot_ender.LootVault} instead, so nothing here needs vanilla's roll, and
 * whatever reads the chest finds it empty.
 */
@Mixin(RandomizableContainer.class)
public interface LootUnpackMixin {

	@Inject(method = "unpackLootTable", at = @At("HEAD"), cancellable = true, require = 1)
	private void lootender$keepTable(Player player, CallbackInfo ci) {
		RandomizableContainer self = (RandomizableContainer) this;
		if (self.getLevel() instanceof ServerLevel && self.getLootTable() != null) ci.cancel();
	}
}
