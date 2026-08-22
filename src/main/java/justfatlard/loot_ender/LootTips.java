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

	/**
	 * Below it, so a spent chest says so first and then says what it is.
	 *
	 * <p>Higher speaks first, and an unspent chest only has the one line to give anyway.
	 */
	private static final int LABEL_PRIORITY = 5;

	/**
	 * What is odd about this chest, in the voice the other tips use.
	 *
	 * <p>Two facts, neither of them visible: that what is inside is not shared, and that nothing
	 * goes back in. The first leads because it is the one that changes what people do - somebody
	 * who does not know it will hang back and let a friend go first, or feel robbed arriving
	 * second at an empty room. That it is not an ordinary chest needs no separate clause; a chest
	 * with anything at all to say about itself has already said that much.
	 *
	 * <p>Shaped like its neighbours on the card: a plain statement with a comma qualifier, the
	 * same as "Carries redstone, lossless" and "No water in range". An earlier draft listed three
	 * clauses joined by a dash and read like a sign rather than a remark.
	 */
	private static final String LABEL = "Yours alone, take only";

	public static void register() {
		BlockTipApi.describe(PRIORITY, (level, pos, state, player) -> {
			if (!(level instanceof ServerLevel serverLevel)) return null;

			return LootVault.get(serverLevel).isSpent(player.getUUID(), pos)
				? "Emptied"
				: null;
		});

		BlockTipApi.describe(LABEL_PRIORITY, (level, pos, state, player) ->
			level instanceof ServerLevel serverLevel && LootOpening.isLootContainer(serverLevel, pos)
				? LABEL
				: null);
	}
}
