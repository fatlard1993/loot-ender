package justfatlard.loot_ender;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Opens and shuts the real block's lid for players who are not, as far as it knows, in it.
 *
 * <p>The obvious way to do this was to hand the block's own {@code startOpen} and
 * {@code stopOpen} the player, and it worked for about a second at a time. Vanilla's opener
 * counter does not trust its own tally: a few ticks after every change it recounts, by asking
 * which nearby entities have <em>this</em> container open. Ours have their own copy open
 * instead, so the recount always came back nought, shut the lid under whoever was standing
 * there, and left the counter one below where it started. The next open took it from minus one
 * to nought, which is not the nought-to-one that opens a lid, so the lid then did the opposite
 * of whatever was happening - open when shut and shut when open.
 *
 * <p>So the tally is kept here, where it can be true, and the lid is driven directly. Block
 * event 1 is what the counter would eventually have sent anyway; a barrel has no lid to
 * animate and carries its state in the blockstate instead.
 */
public final class LootLid {
	private LootLid() {}

	private record Spot(ResourceKey<Level> level, BlockPos pos) {}

	/** How many players have their own copy of each container open. */
	private static final Map<Spot, Integer> open = new HashMap<>();

	public static void opened(ServerLevel level, BlockPos pos) {
		Spot spot = new Spot(level.dimension(), pos.immutable());
		int now = open.merge(spot, 1, Integer::sum);
		if (now == 1) apply(level, pos, true);
	}

	public static void closed(ServerLevel level, BlockPos pos) {
		Spot spot = new Spot(level.dimension(), pos.immutable());
		Integer was = open.get(spot);
		if (was == null) return;

		int now = was - 1;
		if (now > 0) {
			open.put(spot, now);
			return;
		}

		open.remove(spot);
		apply(level, pos, false);
	}

	private static void apply(ServerLevel level, BlockPos pos, boolean opening) {
		BlockState state = level.getBlockState(pos);

		if (state.hasProperty(BarrelBlock.OPEN)) {
			level.setBlock(pos, state.setValue(BarrelBlock.OPEN, opening), Block.UPDATE_ALL);
			play(level, pos, opening ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE);
			return;
		}

		// What the opener counter sends when its own tally crosses zero: id 1, and the lid
		// controller reads any positive count as open.
		level.blockEvent(pos, state.getBlock(), 1, opening ? 1 : 0);
		play(level, pos, opening ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE);
	}

	private static void play(ServerLevel level, BlockPos pos, SoundEvent sound) {
		level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
			sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
	}
}
