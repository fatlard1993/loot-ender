package justfatlard.loot_ender;

import java.util.List;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shows a player which loot they have already taken.
 *
 * <p>This is the one thing the mod has to say out loud, and it says only that.
 * An unopened loot chest looks exactly like any other chest, because as far as
 * the player needs to care it is one: walk up and open it. What vanilla cannot
 * tell you, once loot is per player, is that you personally have nothing left
 * here, and that is what the mark is for.
 *
 * <p>Drawn by Pandorical, which carries the texture. A client without it sees
 * ordinary chests everywhere and loses nothing but the reminder.
 */
public final class LootMarks {
	private LootMarks() {}

	private static final Identifier TEXTURE =
		Identifier.fromNamespaceAndPath("pandorical", "entity/chest/looted");

	/** State every chest this player has emptied. For a join, when they know nothing. */
	public static void restate(ServerPlayer player) {
		PandoricalApi.chestOverlays().replace(player, TEXTURE,
			LootVault.get(player.level()).spentFor(player.getUUID()));
	}

	/** Add or drop one chest's mark as the player's copy empties or fills again. */
	public static void refresh(ServerPlayer player, BlockPos pos, boolean spent) {
		List<BlockPos> one = List.of(pos);
		if (spent) {
			PandoricalApi.chestOverlays().add(player, TEXTURE, one);
		} else {
			PandoricalApi.chestOverlays().remove(player, one);
		}
	}
}
