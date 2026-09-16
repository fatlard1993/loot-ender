package justfatlard.loot_ender.lock;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** One lock, part way picked. Lives only as long as the screen showing it. */
public final class LockAttempt {
	public final LockTier tier;
	/** The picker's, fixed when the screen opens so an op's change lands on the next lock. */
	public final LockDifficulty difficulty;
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
	 * <p>Fixed for as long as this screen is up, snapped picks and all: a lock that moved its
	 * answer every time a pick snapped would make everything you learned before the snap
	 * worthless, and learning is the whole game. It goes with the screen, though: a lock that
	 * kept it past a walk-away could be probed for a moment at a time and never cost a pick.
	 */
	final int sweetSpot;
	/** Where the pick is, in degrees off straight up, as the hand last reported it. */
	float angle;
	/** Whether the hand is turning right now. */
	boolean holding;
	/** How far the cylinder has gone, nought to one; it creeps up under the hand and drops on release. */
	float turn;
	/**
	 * How worn the pick in hand is, nought to one; a snap at one. It lasts as long as this
	 * screen does and no longer: a pick is not an item with a bar on it, it is a thing you
	 * either walk away from whole or do not.
	 */
	float wear;
	/** Ticks the cylinder has been all the way round: a lock opens when it has been held there a moment. */
	int heldOpen;
	/** Whether the pick is held against a jam, which is when it trembles and wears. */
	boolean shaking;
	/** Ticks left showing the last pick in two pieces before the next one is offered. */
	int broken;

	LockAttempt(LockTier tier, LockDifficulty difficulty, BlockPos pos, long key, boolean remembered,
			int sweetSpot, Consumer<ServerPlayer> open) {
		this.tier = tier;
		this.difficulty = difficulty;
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
		return (tier.tolerance + 0.5F) * difficulty.window * LockScreen.SWEEP / tier.span();
	}

	/**
	 * Nought at the far end of the arc from the answer, one dead on. Measured against the whole
	 * sweep rather than half of it, so it only reads nought at one place: a lock that turned not
	 * at all from half its arc would be telling the hand nothing about most of its positions.
	 */
	float closeness() {
		return Math.max(0F, 1F - Math.abs(angle - answerDegrees()) / LockScreen.SWEEP);
	}

	/** Whether the pick is where the cylinder will go all the way round. */
	boolean turns() {
		return Math.abs(angle - answerDegrees()) <= toleranceDegrees();
	}

	/**
	 * How far the cylinder goes with the pick here: all the way on the answer, most of the way
	 * beside it, less further out. Cubed, so the turn grows faster the nearer the pick gets: next
	 * to nothing from across the arc, a lot from a notch or two off, and the cliff up to all the
	 * way is what says the answer has been found. It was squared, which read out the distance
	 * clearly enough from anywhere that a lock could be walked in on without thinking.
	 */
	float reach() {
		if (turns()) return 1F;
		return (float) Math.pow(closeness(), difficulty.feedback) * 0.85F;
	}

	/** How fast a pick wears against a jam, per tick, as far off as it can be. */
	float stressRate() {
		return tier.stressRate * difficulty.wear;
	}
}
