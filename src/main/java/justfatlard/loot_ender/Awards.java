package justfatlard.loot_ender;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advancements this mod hands out.
 *
 * <p>Declared with the impossible trigger, which is how the game says "only code grants this". A
 * lock coming open is a minute of somebody's hands on a screen and no item changing hands at the
 * end of it, so there is nothing for a vanilla criterion to watch.
 */
public final class Awards {
	private Awards() {}

	/** A lock of the hardest kind came open. */
	public static void pickedMasterwork(ServerPlayer player) {
		award(player, "masterwork");
	}

	private static void award(ServerPlayer player, String path) {
		if (player.level().getServer() == null) return;
		AdvancementHolder holder = player.level().getServer().getAdvancements()
			.get(Identifier.fromNamespaceAndPath(Main.MOD_ID, path));
		if (holder == null) return;

		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		if (progress.isDone()) return;
		for (String criterion : progress.getRemainingCriteria()) {
			player.getAdvancements().award(holder, criterion);
		}
	}
}
