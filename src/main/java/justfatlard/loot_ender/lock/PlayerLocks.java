package justfatlard.loot_ender.lock;

import java.util.List;
import java.util.UUID;

import justfatlard.loot_ender.LootEnderConfig;
import justfatlard.loot_ender.integration.ChestUtilsLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Picking somebody else's claim, where Chest Utils is installed to make one.
 *
 * <p>Off unless a server says otherwise, and the reason is that this is not a loot chest. A
 * dungeon's lock is between a player and a pile the world put there; a player's lock is between
 * two players, and turning that into a puzzle with a solution is a decision about how a server
 * treats theft, not a difficulty setting. So the default changes nothing and the two settings
 * that do are described in the config file rather than discovered in somebody's base.
 *
 * <p>Every path out of here that is not "the lock screen is now open" returns null, which hands
 * the chest back to Chest Utils to refuse the way it always has. A player who cannot pick this
 * lock should hear the same knock and the same name they heard before this feature existed.
 */
public final class PlayerLocks {
	private PlayerLocks() {}

	/**
	 * @return SUCCESS when the lock screen has been opened and the chest must not, or null to
	 *         leave the chest to whoever would have handled it
	 */
	public static InteractionResult tryPick(ServerLevel level, ServerPlayer player, BlockState state,
			BlockPos pos) {
		if (!LootEnderConfig.lockpicking()) return null;

		LootEnderConfig.PlayerLocks mode = LootEnderConfig.playerLocks();
		if (mode == LootEnderConfig.PlayerLocks.NEVER) return null;

		if (!ChestUtilsLocks.refuses(level, player, state, pos)) return null;

		// Nothing to pick with is Chest Utils' refusal to give, because it names the owner and this
		// would not. There is no longer a second reason: the lock is picked by looking at it, and
		// a block, a sound and a held key are things every client already has.
		if (!Lockpicking.hasPick(player)) return null;

		if (mode == LootEnderConfig.PlayerLocks.ABSENT && !abandoned(level, state, pos)) return null;

		// Named, because picking somebody's lock should not be something you can do without
		// noticing whose it was.
		String owner = ChestUtilsLocks.lockedBy(level, state, pos);
		if (owner != null) {
			player.sendSystemMessage(Component.translatable("loot-ender.lock.picking_claim", owner));
		}

		Lockpicking.pickPlayerLock(player, pos, picked -> show(level, picked, state, pos));
		return InteractionResult.SUCCESS;
	}

	/** Whether everybody the lock lets in has been away long enough to count as gone. */
	private static boolean abandoned(ServerLevel level, BlockState state, BlockPos pos) {
		List<UUID> keyholders = ChestUtilsLocks.keyholders(level, state, pos);
		// Nobody found is not the same as nobody home. A lock we could not read is one we leave.
		if (keyholders.isEmpty()) return false;

		int days = LootEnderConfig.playerLockAbsentDays();
		for (UUID keyholder : keyholders) {
			if (!LastSeen.absentFor(level.getServer(), keyholder, days)) return false;
		}
		return true;
	}

	/**
	 * Hand over the chest that was behind the lock.
	 *
	 * <p>Runs after the screen, so the chest may have been mined or the pair broken up while it
	 * was open; every step here is allowed to find nothing and give up quietly.
	 */
	private static void show(ServerLevel level, ServerPlayer player, BlockState state, BlockPos pos) {
		if (!level.getBlockState(pos).equals(state)) return;

		Container container;
		if (state.getBlock() instanceof ChestBlock chest) {
			container = ChestBlock.getContainer(chest, state, level, pos, false);
		} else {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			container = blockEntity instanceof Container plain ? plain : null;
		}
		if (container == null) return;

		int rows = container.getContainerSize() / 9;
		if (rows <= 0) return;

		ChestUtilsLocks.open(player, container, state.getBlock().getName(), rows);
	}
}
