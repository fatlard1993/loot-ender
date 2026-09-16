package justfatlard.loot_ender.lock;

import java.util.Map;

import justfatlard.loot_ender.LootEnderConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * How good a chest's lock is, read off what the chest is worth.
 *
 * <p>There is no "value" on a loot table to sort by, so the good ones are named and everything
 * else - including every table a mod invented - falls to {@link #SIMPLE}. That default is the
 * safe one: simple locks are the only tier that can come up unlocked, so an unrecognised table
 * behaves like an ordinary chest most of the time and is never a wall.
 */
public enum LockTier {
	/** No lock at all. Not every chest is worth locking, and a lock means more when some are not. */
	UNLOCKED(0, 0, 0F, ""),
	SIMPLE(9, 1F, 0.020F, "loot-ender.lock.simple"),
	STURDY(13, 0.75F, 0.030F, "loot-ender.lock.sturdy"),
	INTRICATE(17, 0.5F, 0.045F, "loot-ender.lock.intricate"),
	MASTERWORK(21, 0.25F, 0.065F, "loot-ender.lock.masterwork");

	/** Notches the pick can sit in, across the whole arc. More notches, finer the search. */
	public final int positions;
	/**
	 * How many notches either side of the sweet spot still turn the lock, on top of the half
	 * notch the sweet spot is itself: fractions narrow it inside a notch. Across the arc, the
	 * answer is about 37% of it on a simple lock, 21% sturdy, 12% intricate and 7% masterwork.
	 * It was twice that, and a lock found by sweeping across it was not a lock that was picked.
	 */
	public final float tolerance;
	/** Chance the pick snaps on a torque as far from the sweet spot as it is possible to be. */
	/**
	 * How fast a pick held against a jam wears, per tick, when the pick is as far off as it can
	 * be; nearer wears slower. A simple lock gives about two and a half seconds of forcing a
	 * bad angle before the snap, a masterwork under one.
	 */
	public final float stressRate;
	public final String nameKey;

	LockTier(int positions, float tolerance, float stressRate, String nameKey) {
		this.positions = positions;
		this.tolerance = tolerance;
		this.stressRate = stressRate;
		this.nameKey = nameKey;
	}

	/**
	 * The named tables, by the path under {@code minecraft:chests/}. Anything absent is SIMPLE.
	 *
	 * <p>Grouped by what the chest is for rather than by where it is: a mansion and an end city
	 * are both places you arrive at the end of a long trip, and a village house and a shipwreck's
	 * supply crate are both things you walk past.
	 */
	private static final Map<String, LockTier> BY_TABLE = Map.ofEntries(
		Map.entry("chests/end_city_treasure", MASTERWORK),
		Map.entry("chests/woodland_mansion", MASTERWORK),
		Map.entry("chests/bastion_treasure", MASTERWORK),
		Map.entry("chests/ancient_city_ice_box", MASTERWORK),
		Map.entry("chests/stronghold_library", MASTERWORK),

		Map.entry("chests/ancient_city", INTRICATE),
		Map.entry("chests/buried_treasure", INTRICATE),
		Map.entry("chests/nether_bridge", INTRICATE),
		Map.entry("chests/bastion_bridge", INTRICATE),
		Map.entry("chests/bastion_hoglin_stable", INTRICATE),
		Map.entry("chests/bastion_other", INTRICATE),
		Map.entry("chests/stronghold_crossing", INTRICATE),
		Map.entry("chests/stronghold_corridor", INTRICATE),
		Map.entry("chests/jungle_temple", INTRICATE),
		Map.entry("chests/jungle_temple_dispenser", INTRICATE),
		Map.entry("chests/desert_pyramid", INTRICATE),
		Map.entry("chests/pillager_outpost", INTRICATE),
		Map.entry("chests/underwater_ruin_big", INTRICATE),

		Map.entry("chests/simple_dungeon", STURDY),
		Map.entry("chests/abandoned_mineshaft", STURDY),
		Map.entry("chests/shipwreck_treasure", STURDY),
		Map.entry("chests/igloo_chest", STURDY),
		Map.entry("chests/underwater_ruin_small", STURDY),
		Map.entry("chests/ruined_portal", STURDY),
		Map.entry("chests/village/village_weaponsmith", STURDY),
		Map.entry("chests/village/village_toolsmith", STURDY),
		Map.entry("chests/village/village_armorer", STURDY));

	/** The tier this table's chests are built to, before deciding whether this one is locked. */
	public static LockTier of(ResourceKey<LootTable> table) {
		LockTier named = BY_TABLE.get(table.identifier().getPath());
		if (named != null) return named;

		// Trial chamber rewards come in a family that keeps growing, and the ominous ones are
		// the good half of it. Matched by prefix so a new one is covered the day it ships.
		String path = table.identifier().getPath();
		if (path.startsWith("chests/trial_chambers/reward_ominous")) return MASTERWORK;
		if (path.startsWith("chests/trial_chambers/")) return INTRICATE;

		return SIMPLE;
	}

	/**
	 * The lock actually on this chest, which for a simple one is usually no lock.
	 *
	 * <p>Decided from the chest rather than rolled, so the answer is the same every time anyone
	 * asks: whether a chest has a lock is a fact about the chest, not about the visit.
	 */
	public static LockTier on(ResourceKey<LootTable> table, BlockPos pos, long seed) {
		LockTier tier = of(table);
		if (tier != SIMPLE) return tier;

		float chance = LootEnderConfig.commonLockChance();
		if (chance <= 0F) return UNLOCKED;
		if (chance >= 1F) return SIMPLE;

		// Mixed rather than added: neighbouring chests in one structure share a seed and sit a
		// few blocks apart, and a sum would hand them all the same answer.
		long mixed = net.minecraft.util.Mth.getSeed(pos.getX(), pos.getY(), pos.getZ()) * 31L + seed;
		int roll = Math.floorMod((int) (mixed ^ (mixed >>> 32)), 1000);

		return roll < Math.round(chance * 1000F) ? SIMPLE : UNLOCKED;
	}

	/** The furthest apart two notches on this arc can be, which is what closeness is measured against. */
	public int span() {
		return Math.max(1, positions - 1);
	}
}
