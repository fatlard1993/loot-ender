package justfatlard.loot_ender.lock;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** One lock, part way picked. Lives only as long as the screen showing it. */
public final class LockAttempt {
	public final LockTier tier;
	/** Where the lock is, for the sounds. */
	public final BlockPos pos;
	/** What it is remembered under, which for a chest minecart is not its position. */
	public final long key;
	/** Whether picking this one stays picked. A player's claim does not; a loot chest's does. */
	public final boolean remembered;
	/** Reopens the thing the lock was on, once the lock is off it. */
	public final Consumer<ServerPlayer> open;

	/** Minted by the screen builder when the screen opens, so it lands after construction. */
	String screenId;

	/**
	 * The notch that turns it.
	 *
	 * <p>Fixed for the life of the lock, not rerolled per attempt: a lock that moved its answer
	 * every time a pick snapped would make everything you learned before the snap worthless, and
	 * learning is the whole game.
	 */
	final int sweetSpot;
	/** Where the pick is, in degrees off straight up, as the hand last reported it. */
	float angle;
	/** Whether the hand is turning right now. */
	boolean holding;
	/** How far the cylinder has gone, nought to one. */
	float turn;
	/** How much forcing the pick has taken against a jam; a snap at one. */
	float stress;
	/** Ticks the cylinder has been all the way round: a lock opens when it has been held there a moment. */
	int heldOpen;
	/** Whether the pick was drawn trembling last time anyone was told. */
	boolean shaking;

	LockAttempt(LockTier tier, BlockPos pos, long key, boolean remembered, int sweetSpot,
			Consumer<ServerPlayer> open) {
		this.tier = tier;
		this.pos = pos;
		this.key = key;
		this.remembered = remembered;
		this.sweetSpot = sweetSpot;
		this.open = open;
	}

	/** The notch that turns it, as an angle the pick can be held at. */
	float answerDegrees() {
		return net.minecraft.util.Mth.lerp(sweetSpot / (float) tier.span(), -LockScreen.SWEEP / 2F, LockScreen.SWEEP / 2F);
	}

	/** How wide the answer is: the lock's tolerance in notches, as degrees either side. */
	float toleranceDegrees() {
		return (tier.tolerance + 0.5F) * LockScreen.SWEEP / tier.span();
	}

	/** Nought far off, one dead on. */
	float closeness() {
		return Math.max(0F, 1F - Math.abs(angle - answerDegrees()) / (LockScreen.SWEEP / 2F));
	}

	/** Whether the pick is where the cylinder will go all the way round. */
	boolean turns() {
		return Math.abs(angle - answerDegrees()) <= toleranceDegrees();
	}

	/** How far the cylinder goes with the pick here: all the way on the answer, most of the way beside it, less further out. */
	float reach() {
		return turns() ? 1F : closeness() * 0.85F;
	}
}
