package justfatlard.loot_ender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Where the loot chests are, so one can be marked before anybody opens it.
 *
 * <p>The vault only learns of a chest when somebody opens it, which is too late for a mark whose
 * whole job is to be read on the way in. So chests are noticed as their chunks arrive instead:
 * a loaded chunk already holds its block entities in a map, and picking the ones still carrying
 * an unrolled loot table out of it costs a walk over that map and nothing else.
 *
 * <p>Read off {@code getLootTable} rather than the contents, for the same reason the tip is:
 * asking a randomizable container what is inside it is what rolls the table.
 *
 * <p>Each chest is noted with its {@link LootKey} as it stood when its chunk arrived, so asking
 * whether a player has emptied one never reads the world: these are asked while a chunk is still
 * loading, and of chests whose chunks have long since unloaded, and a world read from either loads
 * a chunk, the first from inside its own load.
 *
 * <p>Kept for the life of the server rather than dropped when a chunk unloads. A position is a
 * long, exploring is the only thing that adds any, and a player who walks back to a village
 * should not have to wait for a chunk to reload before the marks come back.
 */
public final class LootIndex {
	private LootIndex() {}

	private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> known = new HashMap<>();

	/** Note every loot chest in a chunk that has just arrived. @return the ones not seen before */
	public static List<BlockPos> noticed(ServerLevel level, LevelChunk chunk) {
		Map<BlockPos, Long> here = known.computeIfAbsent(level.dimension(), key -> new HashMap<>());
		List<BlockPos> fresh = new ArrayList<>();

		for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
			if (!(entry.getValue() instanceof RandomizableContainer container)) continue;
			if (container.getLootTable() == null) continue;

			BlockPos pos = entry.getKey().immutable();
			if (here.put(pos, LootKey.of(entry.getValue(), pos)) == null) fresh.add(pos);
		}
		return fresh;
	}

	/** Every loot chest known in this level, less the ones this player has already emptied. */
	public static List<BlockPos> unopenedFor(ServerLevel level, ServerPlayer player) {
		Map<BlockPos, Long> here = known.get(level.dimension());
		if (here == null || here.isEmpty()) return List.of();

		LootVault vault = LootVault.get(level);
		List<BlockPos> waiting = new ArrayList<>();
		for (Map.Entry<BlockPos, Long> chest : here.entrySet()) {
			if (!vault.isSpent(player.getUUID(), chest.getValue())) waiting.add(chest.getKey());
		}
		return waiting;
	}

	/**
	 * Every loot chest known in this level that this player has emptied and that is filed by its
	 * own identity: the ones the vault cannot place, because they may have moved since.
	 */
	public static List<BlockPos> carriedSpentFor(ServerLevel level, ServerPlayer player) {
		Map<BlockPos, Long> here = known.get(level.dimension());
		if (here == null || here.isEmpty()) return List.of();

		LootVault vault = LootVault.get(level);
		List<BlockPos> spent = new ArrayList<>();
		for (Map.Entry<BlockPos, Long> chest : here.entrySet()) {
			long key = chest.getValue();
			if (LootKey.isIdentity(key) && vault.isSpent(player.getUUID(), key)) spent.add(chest.getKey());
		}
		return spent;
	}

	/** Whether this player has emptied the chest noted here; false for one never noted. */
	public static boolean isSpent(ServerLevel level, ServerPlayer player, BlockPos pos) {
		Map<BlockPos, Long> here = known.get(level.dimension());
		Long key = here == null ? null : here.get(pos);
		return key != null && LootVault.get(level).isSpent(player.getUUID(), key);
	}

	/** A chest noted here was just handed its identity. */
	public static void claimed(ServerLevel level, BlockPos pos, long key) {
		Map<BlockPos, Long> here = known.get(level.dimension());
		if (here != null) here.replace(pos.immutable(), key);
	}

	/** A chest that is no longer there, or no longer loot. */
	public static void forget(ServerLevel level, BlockPos pos) {
		Map<BlockPos, Long> here = known.get(level.dimension());
		if (here != null) here.remove(pos);
	}
}
