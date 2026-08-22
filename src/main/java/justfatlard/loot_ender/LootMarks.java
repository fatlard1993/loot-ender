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
 * <p>Drawn by Pandorical, from textures this mod ships and Pandorical syncs to the client. A
 * client without Pandorical sees ordinary chests everywhere and loses nothing but the reminder.
 */
public final class LootMarks {
	private LootMarks() {}

	/** The clasp gone dark: this one has nothing left for you. */
	private static final Identifier LOOTED =
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "entity/chest/looted");

	/** The clasp in gold: a loot chest you have not been into. */
	private static final Identifier SEALED =
		Identifier.fromNamespaceAndPath(Main.MOD_ID, "entity/chest/sealed");

	/**
	 * State both marks in full. For a join, when the client remembers nothing.
	 *
	 * <p>Two calls because the API keeps a texture's marks separate from the rest, so each can be
	 * stated as a whole without disturbing the other.
	 */
	public static void restate(ServerPlayer player) {
		PandoricalApi.chestOverlays().replace(player, LOOTED,
			LootVault.get(player.level()).spentFor(player.getUUID()));
		PandoricalApi.chestOverlays().replace(player, SEALED,
			LootIndex.unopenedFor(player.level(), player));
	}

	/**
	 * Move one chest between the two marks as the player's copy empties or fills again.
	 *
	 * <p>Never unmarked, only swapped: a loot chest is always one or the other, and a chest with
	 * no mark at all is how an ordinary chest looks.
	 */
	public static void refresh(ServerPlayer player, BlockPos pos, boolean spent) {
		PandoricalApi.chestOverlays().add(player, spent ? LOOTED : SEALED, List.of(pos));
	}

	/** Newly loaded loot chests, for everyone who might be looking at them. */
	public static void noticed(ServerPlayer player, List<BlockPos> positions) {
		if (positions.isEmpty()) return;
		PandoricalApi.chestOverlays().add(player, SEALED, positions);
	}

	/** A chest that has stopped being one. */
	public static void gone(ServerPlayer player, BlockPos pos) {
		PandoricalApi.chestOverlays().remove(player, List.of(pos));
	}
}
