package justfatlard.loot_ender;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Decides whether a container being opened is ours, and opens the player's own
 * copy when it is.
 *
 * <p>Ours means: every part of it still carries an unrolled loot table. A chest
 * somebody has been storing wheat in is not a loot chest and never becomes one,
 * and a double chest with a table on only one half is a shape we do not
 * understand, so both fall through to vanilla untouched. Refusing the cases we
 * are unsure of is the whole safety story here: the worst outcome would be
 * standing between a player and an ordinary chest.
 */
public final class LootOpening {
	private LootOpening() {}

	private static final Component TITLE = Component.translatable("container.chest");

	/**
	 * @return the result to hand back to the caller, or null to let vanilla do
	 *         what it was going to do
	 */
	public static InteractionResult tryOpen(ServerLevel level, ServerPlayer player, BlockState state, BlockPos pos) {
		if (player.isSpectator()) return null;

		List<BlockPos> parts = parts(level, state, pos);
		if (parts.isEmpty()) return null;

		LootVault vault = LootVault.get(level);
		List<Container> copies = new ArrayList<>(parts.size());
		for (BlockPos part : parts) {
			RandomizableContainer container = lootContainer(level, part);
			// Checked again per part: parts() only told us the shape.
			if (container == null || container.getLootTable() == null) return null;

			ResourceKey<LootTable> table = container.getLootTable();
			copies.add(vault.copyFor(level, player, part, table, container.getLootTableSeed()));
		}

		Container opened = copies.size() == 2
			? new LootDoubleContainer(copies.get(0), copies.get(1))
			: copies.get(0);

		// chest-utils, if it is here, for the take-the-lot and top-off buttons.
		if (justfatlard.loot_ender.integration.ChestUtilsScreen.show(
				player, opened, TITLE, copies.size() == 2 ? 6 : 3)) {
			return InteractionResult.SUCCESS;
		}

		player.openMenu(new SimpleMenuProvider(
			(syncId, inventory, opener) -> copies.size() == 2
				? ChestMenu.sixRows(syncId, inventory, opened)
				: ChestMenu.threeRows(syncId, inventory, opened),
			TITLE));

		return InteractionResult.SUCCESS;
	}

	/**
	 * The same, for a chest minecart.
	 *
	 * <p>A cart is an entity, so none of the block machinery above reaches it: the loot table
	 * hangs off the vehicle and vanilla unpacks it into the cart itself the moment anybody
	 * looks inside. First one down a mineshaft took the lot, which is the whole thing this mod
	 * exists to stop, and it read as loot-ender simply not being installed.
	 *
	 * <p>Three rows, always. A cart has no double form to account for.
	 */
	public static InteractionResult tryOpenVehicle(ServerLevel level, ServerPlayer player,
			net.minecraft.world.entity.vehicle.ContainerEntity cart, java.util.UUID id,
			BlockPos where) {
		if (player.isSpectator()) return null;

		ResourceKey<LootTable> table = cart.getContainerLootTable();
		if (table == null) return null;

		PlayerLootContainer copy = LootVault.get(level)
			.copyForVehicle(level, player, id, where, table, cart.getContainerLootTableSeed());

		if (justfatlard.loot_ender.integration.ChestUtilsScreen.show(player, copy, TITLE, 3)) {
			return InteractionResult.SUCCESS;
		}

		player.openMenu(new SimpleMenuProvider(
			(syncId, inventory, opener) -> ChestMenu.threeRows(syncId, inventory, copy), TITLE));

		return InteractionResult.SUCCESS;
	}

	/**
	 * The block positions this container is made of: one, or two for a double
	 * chest. Empty when we should not be involved.
	 */
	private static List<BlockPos> parts(ServerLevel level, BlockState state, BlockPos pos) {
		if (!(state.getBlock() instanceof ChestBlock)) {
			// Barrels and anything else single-block: ours only if it holds loot.
			return lootContainer(level, pos) != null ? List.of(pos) : List.of();
		}

		ChestType type = state.getValue(ChestBlock.TYPE);
		if (type == ChestType.SINGLE) {
			return lootContainer(level, pos) != null ? List.of(pos) : List.of();
		}

		BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
		if (lootContainer(level, pos) == null || lootContainer(level, other) == null) return List.of();

		// Vanilla puts the left half's slots first, and a chest whose contents
		// reshuffle depending on which half you clicked would be its own bug.
		return type == ChestType.LEFT ? List.of(pos, other) : List.of(other, pos);
	}

	/** The container at this position if it is still holding an unrolled loot table. */
	/**
	 * Whether this block is one of ours: a container still carrying an unrolled loot table.
	 *
	 * <p>Reads the table off the block entity rather than reaching for its contents. Asking a
	 * randomizable container what is in it is what rolls the table, and a tip is drawn four times
	 * a second at anything a player happens to look at.
	 */
	public static boolean isLootContainer(ServerLevel level, BlockPos pos) {
		return lootContainer(level, pos) != null;
	}

	private static RandomizableContainer lootContainer(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof RandomizableContainer container)) return null;

		return container.getLootTable() != null ? container : null;
	}
}
