package justfatlard.loot_ender;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Everybody's private copy of every loot chest they have opened.
 *
 * <p>The chest itself is never unpacked. Its loot table stays on the block
 * forever, unrolled, which is what lets the next player get a first look at it
 * too. What a player takes comes out of the copy made here on their first open,
 * so arriving second at a dungeon costs nothing.
 *
 * <p>Only chests somebody walked away from mid-empty are kept as inventories.
 * Once a copy runs out it is thrown away and all that is remembered is the
 * position, because the only question left to answer about it is "anything here
 * for me?" and the answer is no. Copies are take-only, so a full one can only
 * ever shrink and the common ending is a single number rather than 27 slots.
 *
 * <p>The roll is seeded from the chest's own seed mixed with the player's UUID:
 * stable, so the same player reopening an untouched chest before ever taking
 * anything sees the same thing, and different per player, so two people do not
 * find identical chests and conclude the loot is fake.
 */
public final class LootVault extends SavedData {
	private static final String STORAGE_KEY = "loot_ender_vault";

	/** A chest's worth. Double chests are two of these, exactly as vanilla stores them. */
	public static final int SLOTS = 27;

	private record Entry(UUID player, long pos, List<ItemStack> items) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
			Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
			ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Entry::items)
		).apply(instance, Entry::new));
	}

	public static final Codec<LootVault> CODEC = Entry.CODEC.listOf()
		.xmap(LootVault::fromEntries, LootVault::toEntries);

	private static final SavedDataType<LootVault> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), LootVault::new, CODEC, DataFixTypes.LEVEL);

	/** player -> packed chest position -> a copy still holding something. */
	private final Map<UUID, Map<Long, PlayerLootContainer>> copies = new HashMap<>();

	/** player -> chests they have emptied. All that is left of a spent copy. */
	private final Map<UUID, Set<Long>> spent = new HashMap<>();

	public static LootVault get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	/**
	 * This player's copy of this chest, rolling it on the first ask.
	 *
	 * @param table the chest's own loot table, still unrolled
	 * @param seed  the chest's own loot seed
	 */
	public PlayerLootContainer copyFor(ServerLevel level, ServerPlayer player, BlockPos pos,
			ResourceKey<LootTable> table, long seed) {
		UUID id = player.getUUID();

		// Emptied already: hand back an empty copy rather than rolling a second
		// helping. Not stored, because there is nothing in it to store.
		if (this.spent.getOrDefault(id, Set.of()).contains(pos.asLong())) {
			return new PlayerLootContainer(pos, id, SLOTS, this);
		}

		Map<Long, PlayerLootContainer> mine = this.copies.computeIfAbsent(id, key -> new HashMap<>());
		PlayerLootContainer existing = mine.get(pos.asLong());
		if (existing != null) return existing;

		PlayerLootContainer container = new PlayerLootContainer(pos, id, SLOTS, this);
		fill(level, player, pos, container, table, seed);
		mine.put(pos.asLong(), container);
		this.setDirty();
		return container;
	}

	/**
	 * This player's copy of a chest minecart, rolled on the first ask.
	 *
	 * <p>Filed under the cart's own identity rather than where it happens to be sitting. A cart
	 * is pushed, and a copy keyed to a position would roll fresh loot every time it moved - the
	 * one thing this mod exists to stop.
	 *
	 * <p>Shares the position keyspace on purpose: the packed form of a cart's UUID decodes to a
	 * height no block can occupy, so it cannot collide with a real chest, and nothing about the
	 * stored shape has to change to hold it.
	 */
	public PlayerLootContainer copyForVehicle(ServerLevel level, ServerPlayer player, UUID cart,
			BlockPos where, ResourceKey<LootTable> table, long seed) {
		UUID id = player.getUUID();
		long key = cart.getLeastSignificantBits();

		if (this.spent.getOrDefault(id, Set.of()).contains(key)) {
			return new PlayerLootContainer(key, where, id, SLOTS, this, false);
		}

		Map<Long, PlayerLootContainer> mine = this.copies.computeIfAbsent(id, k -> new HashMap<>());
		PlayerLootContainer existing = mine.get(key);
		if (existing != null) return existing;

		PlayerLootContainer container = new PlayerLootContainer(key, where, id, SLOTS, this, false);
		fill(level, player, where, container, table, seed);
		mine.put(key, container);
		this.setDirty();
		return container;
	}

	/**
	 * Called when a player shuts one of their copies. An empty one stops being an
	 * inventory and becomes a position.
	 */
	void onClosed(ServerPlayer player, PlayerLootContainer container) {
		boolean empty = container.isEmpty();
		if (empty) {
			Map<Long, PlayerLootContainer> mine = this.copies.get(container.owner());
			if (mine != null) mine.remove(container.key());
			this.spent.computeIfAbsent(container.owner(), key -> new HashSet<>())
				.add(container.key());
			this.setDirty();
		}

		// A cart has no block to put a clasp on, and the position it was opened at is not
		// where it will be next time anyway.
		if (container.marked()) LootMarks.refresh(player, container.pos(), empty);
	}

	/**
	 * Whether this one particular chest is spent for this player.
	 *
	 * <p>A set lookup, because block-tip asks this every time somebody looks at a
	 * chest and walking the whole list to answer would scale with how much of the
	 * world they had already looted.
	 */
	public boolean isSpent(UUID player, BlockPos pos) {
		Set<Long> mine = this.spent.get(player);
		return mine != null && mine.contains(pos.asLong());
	}

	/** Every chest this player has already emptied, for restating marks on a join. */
	public List<BlockPos> spentFor(UUID player) {
		Set<Long> mine = this.spent.get(player);
		if (mine == null) return List.of();

		List<BlockPos> out = new ArrayList<>();
		for (long packed : mine) out.add(BlockPos.of(packed));
		return out;
	}

	/** Forget a chest that no longer exists, for everyone who had a copy of it. */
	public void forget(BlockPos pos) {
		boolean removed = false;
		for (Map<Long, PlayerLootContainer> mine : this.copies.values()) {
			removed |= mine.remove(pos.asLong()) != null;
		}
		for (Set<Long> mine : this.spent.values()) {
			removed |= mine.remove(pos.asLong());
		}
		if (removed) this.setDirty();
	}

	private static void fill(ServerLevel level, ServerPlayer player, BlockPos pos,
			PlayerLootContainer container, ResourceKey<LootTable> tableKey, long seed) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
			.withLuck(player.getLuck())
			.withParameter(LootContextParams.THIS_ENTITY, player)
			.create(LootContextParamSets.CHEST);

		table.fill(container, params, mixSeed(seed, player.getUUID(), pos));
	}

	/**
	 * A seed that is this chest, for this player.
	 *
	 * <p>The position is in the mix as well as the chest's own seed, because a
	 * structure placed twice in a world can carry the same seed in both copies,
	 * and two dungeons that pay out identically read as a bug.
	 */
	private static long mixSeed(long chestSeed, UUID player, BlockPos pos) {
		long mixed = chestSeed * 31L + player.getMostSignificantBits();
		mixed = mixed * 31L + player.getLeastSignificantBits();
		return mixed * 31L + pos.asLong();
	}

	/**
	 * An entry with no items is a spent chest. That is the same shape a copy takes
	 * when it has been emptied but not yet closed, so a save caught mid-loot reads
	 * back as spent, which is what it is.
	 */
	private List<Entry> toEntries() {
		List<Entry> entries = new ArrayList<>();

		for (Map.Entry<UUID, Map<Long, PlayerLootContainer>> perPlayer : this.copies.entrySet()) {
			for (Map.Entry<Long, PlayerLootContainer> perChest : perPlayer.getValue().entrySet()) {
				PlayerLootContainer container = perChest.getValue();
				List<ItemStack> items = new ArrayList<>(container.getContainerSize());
				if (!container.isEmpty()) {
					for (int slot = 0; slot < container.getContainerSize(); slot++) {
						items.add(container.getItem(slot));
					}
				}
				entries.add(new Entry(perPlayer.getKey(), perChest.getKey(), items));
			}
		}

		for (Map.Entry<UUID, Set<Long>> perPlayer : this.spent.entrySet()) {
			for (long packed : perPlayer.getValue()) {
				entries.add(new Entry(perPlayer.getKey(), packed, List.of()));
			}
		}

		return entries;
	}

	private static LootVault fromEntries(List<Entry> entries) {
		LootVault vault = new LootVault();
		for (Entry entry : entries) {
			if (entry.items().isEmpty()) {
				vault.spent.computeIfAbsent(entry.player(), key -> new HashSet<>()).add(entry.pos());
				continue;
			}

			BlockPos pos = BlockPos.of(entry.pos());
			PlayerLootContainer container = new PlayerLootContainer(pos, entry.player(), SLOTS, vault);
			for (int slot = 0; slot < Math.min(entry.items().size(), SLOTS); slot++) {
				container.setItem(slot, entry.items().get(slot));
			}
			vault.copies.computeIfAbsent(entry.player(), key -> new HashMap<>())
				.put(entry.pos(), container);
		}
		return vault;
	}
}
