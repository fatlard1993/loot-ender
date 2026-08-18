package justfatlard.loot_ender;

import justfatlard.block_tip.api.BlockTipApi;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether there is anything left in here for you.
 *
 * <p>The darkened clasp says it already, and this says it in words for the
 * player who has not yet worked out what the clasp means. It is also the more
 * useful of the two things a village loot chest could tell you, which is why it
 * speaks first: that it belongs to the village stops being news once you have
 * emptied it and found out.
 */
public final class LootTips {
	private LootTips() {}

	/** Above ownership. Whose it is matters less than whether it is spent. */
	private static final int PRIORITY = 10;

	public static void register() {
		BlockTipApi.describe(PRIORITY, (level, pos, state, player) -> {
			if (!(level instanceof ServerLevel serverLevel)) return null;

			return LootVault.get(serverLevel).isSpent(player.getUUID(), pos)
				? "Emptied"
				: null;
		});
	}
}
