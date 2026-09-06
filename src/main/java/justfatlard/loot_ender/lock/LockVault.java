package justfatlard.loot_ender.lock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which chests each player has already picked.
 *
 * <p>Per player, because the loot is. A chest that stayed open once the first person got through
 * it would hand everyone behind them a shortcut, and this mod exists on the premise that arriving
 * second costs nothing and earns nothing.
 *
 * <p>Its own storage rather than a field on {@link justfatlard.loot_ender.LootVault}: nothing here
 * has to agree with a copy's contents, and keeping it separate means the whole feature can be
 * lifted back out without touching the format worlds already have loot in.
 */
public final class LockVault extends SavedData {
	private static final String STORAGE_KEY = "loot_ender_locks";

	private record Entry(UUID player, List<Long> picked) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
			Codec.LONG.listOf().fieldOf("picked").forGetter(Entry::picked)
		).apply(instance, Entry::new));
	}

	public static final Codec<LockVault> CODEC = Entry.CODEC.listOf()
		.xmap(LockVault::fromEntries, LockVault::toEntries);

	private static final SavedDataType<LockVault> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), LockVault::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, Set<Long>> picked = new HashMap<>();

	public static LockVault get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * Keys are {@link justfatlard.loot_ender.LootVault}'s keyspace, not positions: a chest
	 * minecart is filed under its own identity there because a cart that was keyed to where it
	 * stood would forget everything the moment somebody pushed it.
	 */
	public boolean isPicked(UUID player, long key) {
		return this.picked.getOrDefault(player, Set.of()).contains(key);
	}

	public void remember(UUID player, long key) {
		if (this.picked.computeIfAbsent(player, id -> new HashSet<>()).add(key)) {
			this.setDirty();
		}
	}

	/** A broken chest takes its lock with it, the same way it takes everyone's copy. */
	public void forget(BlockPos pos) {
		boolean changed = false;
		for (Set<Long> theirs : this.picked.values()) {
			changed |= theirs.remove(pos.asLong());
		}
		if (changed) this.setDirty();
	}

	private static LockVault fromEntries(List<Entry> entries) {
		LockVault vault = new LockVault();
		for (Entry entry : entries) {
			vault.picked.put(entry.player(), new HashSet<>(entry.picked()));
		}
		return vault;
	}

	private static List<Entry> toEntries(LockVault vault) {
		return vault.picked.entrySet().stream()
			.filter(entry -> !entry.getValue().isEmpty())
			.map(entry -> new Entry(entry.getKey(), List.copyOf(entry.getValue())))
			.toList();
	}
}
