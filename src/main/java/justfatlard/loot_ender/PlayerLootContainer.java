package justfatlard.loot_ender;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * One player's copy of one chest.
 *
 * <p>The block it stands for is never opened, so the two things a real chest
 * does on being opened have to be done here: the lid has to move and the mark
 * has to be reconsidered. Both hang off {@code startOpen} and {@code stopOpen},
 * which the menu calls for us, and which are also the only two container methods
 * vanilla does not route through {@code unpackLootTable}. That last part is what
 * makes the delegation safe: asking the real block entity to animate cannot
 * trick it into rolling the loot everybody else is still owed.
 *
 * <p>The level is taken from whoever opened it rather than held as a field. A
 * copy outlives a restart, and a field would have to survive being loaded from
 * disk with no level anywhere in reach; the player standing at the chest is
 * always in the same level as the chest.
 */
public class PlayerLootContainer extends SimpleContainer implements TakeOnly {
	private final BlockPos pos;
	private final UUID owner;

	/**
	 * What the vault files this copy under.
	 *
	 * <p>The same as the packed position for a chest in the ground, which is what it was when
	 * that was the only kind. A chest minecart rolls away from wherever it was opened, so it is
	 * filed under its own identity instead and this stops being a position at all.
	 */
	private final long key;

	/** Whether this copy has a clasp on a block somewhere. A cart has no block to mark. */
	private final boolean marked;

	/**
	 * The vault this copy belongs to. SimpleContainer has no listeners, so it is
	 * told directly when the contents move and when the menu closes.
	 */
	private final LootVault vault;

	/** Vanilla's own container reach, squared below. */
	private static final double REACH = 8.0;

	public PlayerLootContainer(BlockPos pos, UUID owner, int size, LootVault vault) {
		this(pos.asLong(), pos, owner, size, vault, true);
	}

	public PlayerLootContainer(long key, BlockPos pos, UUID owner, int size, LootVault vault,
			boolean marked) {
		super(size);
		this.key = key;
		this.pos = pos.immutable();
		this.owner = owner;
		this.vault = vault;
		this.marked = marked;
	}

	public BlockPos pos() {
		return this.pos;
	}

	public long key() {
		return this.key;
	}

	public boolean marked() {
		return this.marked;
	}

	public UUID owner() {
		return this.owner;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		this.vault.setDirty();
	}

	/**
	 * A real chest closes when you walk away from it, and this one has to as well.
	 * SimpleContainer says yes to everyone forever, which detached from a block
	 * means a player could open a chest, leave the room, and keep emptying it.
	 */
	@Override
	public boolean stillValid(Player player) {
		BlockEntity blockEntity = player.level().getBlockEntity(this.pos);
		if (!(blockEntity instanceof Container)) return false;

		return player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5)
			<= REACH * REACH;
	}

	@Override
	public void startOpen(ContainerUser user) {
		super.startOpen(user);
		delegate(user, true);
	}

	@Override
	public void stopOpen(ContainerUser user) {
		super.stopOpen(user);
		delegate(user, false);

		if (user.getLivingEntity() instanceof ServerPlayer player) {
			this.vault.onClosed(player, this);
		}
	}

	/** Move the real chest's lid, so a chest that opens looks like a chest that opened. */
	private void delegate(ContainerUser user, boolean opening) {
		LivingEntity entity = user.getLivingEntity();
		if (entity == null || !(entity.level() instanceof ServerLevel level)) return;

		// Not the block's own startOpen/stopOpen: see LootLid for what that did.
		if (opening) {
			LootLid.opened(level, this.pos);
		} else {
			LootLid.closed(level, this.pos);
		}
	}
}
