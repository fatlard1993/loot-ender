package justfatlard.loot_ender;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import justfatlard.loot_ender.lock.LockDifficulty;
import net.fabricmc.loader.api.FabricLoader;

/** File-backed settings, read once at startup. */
public final class LootEnderConfig {
	private static final Path CONFIG_PATH =
		FabricLoader.getInstance().getConfigDir().resolve("loot-ender.properties");

	private static boolean lockpicking = true;
	private static LockDifficulty lockDifficulty = LockDifficulty.NORMAL;
	private static float commonLockChance = 0.35F;
	private static float lockpickDropChance = 0.05F;
	private static PlayerLocks playerLocks = PlayerLocks.NEVER;
	private static int playerLockAbsentDays = 30;

	/** What lockpicking is allowed to do about another player's claim on a chest. */
	public enum PlayerLocks {
		/** A player's lock is a player's lock. */
		NEVER,
		/** Only once everybody who could open it has stopped turning up. */
		ABSENT,
		/** Any player lock is pickable, by anyone with the picks for it. */
		ALWAYS
	}

	private static final String DEFAULT_CONFIG = """
			# Loot Ender Configuration
			# Delete this file to regenerate with defaults.

			# Whether loot chests carry locks you have to pick before your copy opens.
			# Off leaves every chest opening the way it always did; lockpicks stop
			# dropping and stop appearing in loot, and any you are already carrying
			# stay in your inventory doing nothing.
			lockpicking=true

			# How hard locks are to pick: easiest, easy, normal or hard. Easier widens
			# the spot that turns the lock and wears picks slower; easiest also makes
			# the cylinder show more plainly how near the pick is. An op can set it for
			# one player with /lockpicking difficulty player <name> <level>.
			lock_difficulty=normal

			# Chance that a chest from a common loot table carries a lock at all.
			# The better tables (strongholds, mansions, end cities and the like) are
			# always locked; this is only about the ordinary ones, so that a lock
			# stays a sign the chest is worth something. 0 leaves them all unlocked.
			common_lock_chance=0.35

			# Chance a zombie or skeleton killed by a player drops a lockpick.
			# Looting raises it, the way it raises any rare drop.
			lockpick_drop_chance=0.05

			# Requires Chest Utils, which is where a player-locked chest comes from.
			# What lockpicking may do about somebody else's claim on a chest:
			#   never  - it may not. A player's lock is a player's lock. (default)
			#   absent - only when everybody the lock lets in, owner and shared alike,
			#            has been away for player_lock_absent_days. For reclaiming the
			#            base of a player who is not coming back.
			#   always - any player-locked chest can be picked by anyone with picks.
			#            This turns locks into a delay rather than a claim; it suits a
			#            server that wants theft to be possible but expensive, and it
			#            will surprise anybody who locked a chest expecting otherwise.
			# Player locks are always masterwork: a claim should cost more to break than
			# a dungeon chest does, whichever setting is in force.
			pick_player_locks=never

			# Days away before "absent" counts somebody as gone. Read from when their
			# save file was last written, which is every logout and every autosave.
			player_lock_absent_days=30
			""";

	private LootEnderConfig() {}

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			createDefault();
			return;
		}

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
			props.load(in);
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed to read config, using defaults: {}", Main.MOD_ID, e.getMessage());
			return;
		}

		lockpicking = bool(props, "lockpicking", lockpicking);
		lockDifficulty = difficulty(props, "lock_difficulty", lockDifficulty);
		commonLockChance = fraction(props, "common_lock_chance", commonLockChance);
		lockpickDropChance = fraction(props, "lockpick_drop_chance", lockpickDropChance);
		playerLocks = playerLocks(props, "pick_player_locks", playerLocks);
		playerLockAbsentDays = Math.max(0, integer(props, "player_lock_absent_days", playerLockAbsentDays));
		Main.LOGGER.info("[{}] Config loaded from {}", Main.MOD_ID, CONFIG_PATH);
	}

	public static boolean lockpicking() {
		return lockpicking;
	}

	public static LockDifficulty lockDifficulty() {
		return lockDifficulty;
	}

	public static float commonLockChance() {
		return commonLockChance;
	}

	public static float lockpickDropChance() {
		return lockpickDropChance;
	}

	public static PlayerLocks playerLocks() {
		return playerLocks;
	}

	public static int playerLockAbsentDays() {
		return playerLockAbsentDays;
	}

	private static void createDefault() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, DEFAULT_CONFIG);
			Main.LOGGER.info("[{}] Created default config at {}", Main.MOD_ID, CONFIG_PATH);
		} catch (IOException e) {
			Main.LOGGER.error("[{}] Failed to write default config: {}", Main.MOD_ID, e.getMessage());
		}
	}

	/** An unreadable value falls back to the strictest answer, which is the one that changes nothing. */
	private static PlayerLocks playerLocks(Properties props, String key, PlayerLocks fallback) {
		String value = props.getProperty(key);
		if (value == null) return fallback;
		try {
			return PlayerLocks.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			Main.LOGGER.warn("[{}] Config '{}' is not one of never/absent/always, using {}",
				Main.MOD_ID, key, fallback.name().toLowerCase(java.util.Locale.ROOT));
			return fallback;
		}
	}

	private static LockDifficulty difficulty(Properties props, String key, LockDifficulty fallback) {
		String value = props.getProperty(key);
		if (value == null) return fallback;
		LockDifficulty named = LockDifficulty.named(value);
		if (named != null) return named;
		Main.LOGGER.warn("[{}] Config '{}' is not one of easiest/easy/normal/hard, using {}",
			Main.MOD_ID, key, fallback.getSerializedName());
		return fallback;
	}

	private static int integer(Properties props, String key, int fallback) {
		String value = props.getProperty(key);
		if (value == null) return fallback;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			Main.LOGGER.warn("[{}] Config '{}' is not a whole number, using {}", Main.MOD_ID, key, fallback);
			return fallback;
		}
	}

	private static boolean bool(Properties props, String key, boolean fallback) {
		String value = props.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}

	/** A 0..1 setting, clamped rather than rejected: a typo should not disable a feature silently. */
	private static float fraction(Properties props, String key, float fallback) {
		String value = props.getProperty(key);
		if (value == null) return fallback;
		try {
			return Math.clamp(Float.parseFloat(value.trim()), 0F, 1F);
		} catch (NumberFormatException e) {
			Main.LOGGER.warn("[{}] Config '{}' is not a number, using {}", Main.MOD_ID, key, fallback);
			return fallback;
		}
	}

	/**
	 * Write one value back into the config file, keeping the file's comments and order: the
	 * line for the key is replaced where it stands, or added at the end when it is missing.
	 */
	private static void store(String key, String value) {
		try {
			java.nio.file.Path path = CONFIG_PATH;
			java.util.List<String> lines = java.nio.file.Files.exists(path)
				? new java.util.ArrayList<>(java.nio.file.Files.readAllLines(path))
				: new java.util.ArrayList<>();
			boolean found = false;
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).trim().startsWith(key + "=") || lines.get(i).trim().startsWith(key + " =")) {
					lines.set(i, key + "=" + value);
					found = true;
				}
			}
			if (!found) lines.add(key + "=" + value);
			java.nio.file.Files.createDirectories(path.getParent());
			java.nio.file.Files.write(path, lines);
		} catch (java.io.IOException e) {
			System.err.println("[loot-ender] Could not write config: " + e);
		}
	}

	public static void setLockpicking(boolean on) { lockpicking = on; store("lockpicking", String.valueOf(on)); }
	public static void setLockDifficulty(LockDifficulty difficulty) { lockDifficulty = difficulty; store("lock_difficulty", difficulty.getSerializedName()); }
	public static void setCommonLockChance(float chance) { commonLockChance = chance; store("common_lock_chance", String.valueOf(chance)); }
	public static void setLockpickDropChance(float chance) { lockpickDropChance = chance; store("lockpick_drop_chance", String.valueOf(chance)); }
	public static void setPlayerLocks(PlayerLocks mode) { playerLocks = mode; store("pick_player_locks", mode.name().toLowerCase(java.util.Locale.ROOT)); }
	public static void setPlayerLockAbsentDays(int days) { playerLockAbsentDays = days; store("player_lock_absent_days", String.valueOf(days)); }

	/** The file's knobs in the mod menu, for ops. */
	public static void menu() {
		var group = justfatlard.pandorical.api.PandoricalApi.settings().serverGroup(Main.MOD_ID, "Loot Ender");
		group.toggle("lockpicking", "Lockpicking", true)
			.backedBy(player -> lockpicking(), (player, v) -> setLockpicking(v));
		java.util.Map<String, String> levels = new java.util.LinkedHashMap<>();
		for (LockDifficulty level : LockDifficulty.values()) levels.put(level.getSerializedName(), level.label);
		group.choice("lockDifficulty", "Lock difficulty", levels, "normal")
			.describe("For everyone an op has not set one for, with /lockpicking difficulty")
			.backedBy(player -> lockDifficulty().getSerializedName(), (player, v) -> setLockDifficulty(LockDifficulty.named(v)));
		group.number("commonLockChance", "Locked chests, percent", 0, 100, 5, 35)
			.describe("How many loot chests spawn locked")
			.backedBy(player -> Math.round(commonLockChance() * 100), (player, v) -> setCommonLockChance(v / 100F));
		group.number("lockpickDropChance", "Lockpick drop, percent", 0, 100, 1, 5)
			.backedBy(player -> Math.round(lockpickDropChance() * 100), (player, v) -> setLockpickDropChance(v / 100F));
		java.util.Map<String, String> modes = new java.util.LinkedHashMap<>();
		for (PlayerLocks mode : PlayerLocks.values()) {
			modes.put(mode.name().toLowerCase(java.util.Locale.ROOT), mode.name().charAt(0) + mode.name().substring(1).toLowerCase(java.util.Locale.ROOT));
		}
		group.choice("playerLocks", "Pick player locks", modes, "never")
			.describe("Whether other players' locked chests can be picked")
			.backedBy(player -> playerLocks().name().toLowerCase(java.util.Locale.ROOT),
				(player, v) -> setPlayerLocks(PlayerLocks.valueOf(v.toUpperCase(java.util.Locale.ROOT))));
		group.number("playerLockAbsentDays", "Absent after, days", 1, 365, 1, 30)
			.describe("Days away before an owner counts as absent")
			.backedBy(player -> playerLockAbsentDays(), (player, v) -> setPlayerLockAbsentDays(v));
	}

}
