package justfatlard.loot_ender;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	/** player -> packed chest position -> that player's copy. */
	private final Map<UUID, Map<Long, PlayerLootContainer>> copies = new HashMap<>();

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
		Map<Long, PlayerLootContainer> mine = this.copies.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
		PlayerLootContainer existing = mine.get(pos.asLong());
		if (existing != null) return existing;

		PlayerLootContainer container = newContainer(player.getUUID(), pos);
		fill(level, player, pos, container, table, seed);
		mine.put(pos.asLong(), container);
		this.setDirty();
		return container;
	}

	/** True once this player has a copy of this chest and has emptied it. */
	public boolean isSpent(UUID player, BlockPos pos) {
		Map<Long, PlayerLootContainer> mine = this.copies.get(player);
		if (mine == null) return false;

		PlayerLootContainer container = mine.get(pos.asLong());
		return container != null && container.isEmpty();
	}

	/** Every chest this player has already emptied, for restating marks on a join. */
	public List<BlockPos> spentFor(UUID player) {
		Map<Long, PlayerLootContainer> mine = this.copies.get(player);
		if (mine == null) return List.of();

		List<BlockPos> out = new ArrayList<>();
		for (PlayerLootContainer container : mine.values()) {
			if (container.isEmpty()) out.add(container.pos());
		}
		return out;
	}

	/** Forget a chest that no longer exists, for everyone who had a copy of it. */
	public void forget(BlockPos pos) {
		boolean removed = false;
		for (Map<Long, PlayerLootContainer> mine : this.copies.values()) {
			removed |= mine.remove(pos.asLong()) != null;
		}
		if (removed) this.setDirty();
	}

	private PlayerLootContainer newContainer(UUID player, BlockPos pos) {
		return new PlayerLootContainer(pos, player, SLOTS, this::setDirty);
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

	private List<Entry> toEntries() {
		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<UUID, Map<Long, PlayerLootContainer>> perPlayer : this.copies.entrySet()) {
			for (Map.Entry<Long, PlayerLootContainer> perChest : perPlayer.getValue().entrySet()) {
				PlayerLootContainer container = perChest.getValue();
				List<ItemStack> items = new ArrayList<>(container.getContainerSize());
				for (int slot = 0; slot < container.getContainerSize(); slot++) {
					items.add(container.getItem(slot));
				}
				entries.add(new Entry(perPlayer.getKey(), perChest.getKey(), items));
			}
		}
		return entries;
	}

	private static LootVault fromEntries(List<Entry> entries) {
		LootVault vault = new LootVault();
		for (Entry entry : entries) {
			BlockPos pos = BlockPos.of(entry.pos());
			PlayerLootContainer container = new PlayerLootContainer(pos, entry.player(), SLOTS, vault::setDirty);
			for (int slot = 0; slot < Math.min(entry.items().size(), SLOTS); slot++) {
				container.setItem(slot, entry.items().get(slot));
			}
			vault.copies.computeIfAbsent(entry.player(), key -> new HashMap<>())
				.put(entry.pos(), container);
		}
		return vault;
	}
}
