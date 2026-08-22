package justfatlard.loot_ender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Kept for the life of the server rather than dropped when a chunk unloads. A position is a
 * long, exploring is the only thing that adds any, and a player who walks back to a village
 * should not have to wait for a chunk to reload before the marks come back.
 */
public final class LootIndex {
	private LootIndex() {}

	private static final Map<ResourceKey<Level>, Set<BlockPos>> known = new HashMap<>();

	/** Note every loot chest in a chunk that has just arrived. @return the ones not seen before */
	public static List<BlockPos> noticed(ServerLevel level, LevelChunk chunk) {
		Set<BlockPos> here = known.computeIfAbsent(level.dimension(), key -> new HashSet<>());
		List<BlockPos> fresh = new ArrayList<>();

		for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
			if (!(entry.getValue() instanceof RandomizableContainer container)) continue;
			if (container.getLootTable() == null) continue;

			BlockPos pos = entry.getKey().immutable();
			if (here.add(pos)) fresh.add(pos);
		}
		return fresh;
	}

	/** Every loot chest known in this level, less the ones this player has already emptied. */
	public static List<BlockPos> unopenedFor(ServerLevel level, ServerPlayer player) {
		Set<BlockPos> here = known.get(level.dimension());
		if (here == null || here.isEmpty()) return List.of();

		LootVault vault = LootVault.get(level);
		List<BlockPos> waiting = new ArrayList<>();
		for (BlockPos pos : here) {
			if (!vault.isSpent(player.getUUID(), pos)) waiting.add(pos);
		}
		return waiting;
	}

	/** A chest that is no longer there, or no longer loot. */
	public static void forget(ServerLevel level, BlockPos pos) {
		Set<BlockPos> here = known.get(level.dimension());
		if (here != null) here.remove(pos);
	}
}
