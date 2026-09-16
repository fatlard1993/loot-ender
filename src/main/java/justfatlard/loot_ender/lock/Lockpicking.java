package justfatlard.loot_ender.lock;

import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import justfatlard.pandorical.api.ComponentType;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import justfatlard.loot_ender.LootEnderConfig;
import justfatlard.loot_ender.LootEnderItems;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The lock between a player and their copy of a chest.
 *
 * <p>Played by hand, in real time, the way the good ones are: the pick is swept round the
 * keyhole and set, then pushed, and the cylinder turns - at its own pace, a beat to go all the
 * way round - as far as the pick's position lets it. On the answer it goes all the way and the
 * lock opens. Beside the answer it goes most of the way and jams; further out, less; and a pick
 * held against a jam trembles and wears, and worn through, snaps. The wear is shown on the
 * screen and lasts as long as the screen does; a pick more than half worn when you leave is
 * spent, because a pick is not carried about with a bar on it. How far the cylinder turned
 * before it jammed is the only readout there is, and working the answer out from it is the
 * game. Nothing is ever taken away for failing, only picks, and the lock keeps its answer
 * while you stay at it, so what a snap taught you is still true on the next pick; walk away
 * and it is set afresh, so there is no probing it and coming back.
 *
 * <p>The client owns the pick's motion, so it never waits on the server; the server owns the
 * answer, the cylinder and the pick's wear, so it never leaves the server. The two meet in a
 * handful of small reports a second and the cylinder's angle coming back, which on a bad
 * connection makes the cylinder late rather than the game wrong.
 */
public final class Lockpicking {
	private Lockpicking() {}

	private static final Map<UUID, LockAttempt> attempts = new ConcurrentHashMap<>();

	/**
	 * What a player's claim on a chest is worth as a lock: the best there is, whatever the chest
	 * is made of. A claim somebody made deliberately should cost more to break than a dungeon
	 * left one lying around.
	 */
	private static final LockTier PLAYER_CLAIM_TIER = LockTier.MASTERWORK;

	/** Ticks the cylinder must be held all the way round before the lock gives: a beat, not a flicker. */
	private static final int HELD_OPEN_TICKS = 6;
	/**
	 * How far the cylinder turns each tick under the hand, nought to one: half a second all the
	 * way round. Slow enough that where it stops is watched arriving rather than read off after.
	 */
	private static final float TURN_PER_TICK = 0.1F;
	/**
	 * Ticks a snapped pick lies in two pieces before the hand takes up the next: long enough
	 * to be seen to have happened. A pick that simply vanished was a number going down.
	 */
	private static final int BROKEN_TICKS = 16;
	/**
	 * What every push on the pick scrapes off it, in ticks of forcing against a jam at the
	 * lock's own rate: a probe costs something whether or not it is held. Without it a pick
	 * could be pushed and let go the moment the cylinder stopped, as often as liked, and a
	 * lock read that way for free was a lock with its answer printed on it. About seven
	 * probes wear a pick through on a masterwork lock, and some twenty-five on a simple one.
	 */
	private static final float PRESS_WEAR = 2F;
	/** Wear past which a pick is not worth keeping: it goes when the screen does. */
	private static final float SPENT_WEAR = 0.5F;

	public static void registerHandlers() {
		var screens = PandoricalApi.screens();
		screens.onAction(LockScreen.TYPE, LockScreen.PICK, Lockpicking::handled);
		screens.onClose(LockScreen.TYPE, Lockpicking::give_up);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(Lockpicking::tick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
			(handler, server) -> forget(handler.getPlayer().getUUID()));
	}

	/** A report from the hand on the pick: where it is, and whether it has begun or stopped turning. */
	private static void handled(ServerPlayer player, Map<String, String> data) {
		LockAttempt attempt = attempts.get(player.getUUID());
		if (attempt == null) return;
		float angle;
		try {
			angle = Float.parseFloat(data.getOrDefault(ComponentType.DIAL_ANGLE, "0"));
		} catch (NumberFormatException e) {
			return;
		}
		angle = Math.clamp(angle, -LockScreen.SWEEP / 2F, LockScreen.SWEEP / 2F);
		switch (data.getOrDefault(ComponentType.DIAL_ACTION, "aim")) {
			case "press" -> {
				if (attempt.broken > 0) return;
				attempt.angle = angle;
				attempt.holding = true;
				attempt.heldOpen = 0;
				attempt.shaking = false;
				attempt.wear += attempt.stressRate() * PRESS_WEAR;
				if (attempt.wear >= 1F) {
					snap(player, attempt);
					return;
				}
				// One pitch whatever the angle: the sound must not say what the turn is for saying.
				player.level().playSound(null, attempt.pos, SoundEvents.CHAIN_PLACE,
					SoundSource.BLOCKS, 0.35F, 1.3F);
			}
			case "release" -> letGo(player, attempt);
			default -> {
				// The pick cannot move while it is turning; a report that says otherwise is stale.
				if (!attempt.holding) attempt.angle = angle;
			}
		}
	}

	/** The hand comes off: the cylinder springs back. What the pick has taken, it keeps. */
	private static void letGo(ServerPlayer player, LockAttempt attempt) {
		if (!attempt.holding) return;
		attempt.holding = false;
		attempt.turn = 0F;
		attempt.heldOpen = 0;
		attempt.shaking = false;
		LockScreen.refresh(player, attempt);
	}

	/**
	 * Every tick, every hand on a lock: the cylinder goes round at its own pace until the pick
	 * stops it, and only then, held against the jam, does the pick start to wear.
	 */
	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, LockAttempt> held : List.copyOf(attempts.entrySet())) {
			LockAttempt attempt = held.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(held.getKey());
			if (player == null) continue;

			if (attempt.broken > 0) {
				if (--attempt.broken == 0) LockScreen.refresh(player, attempt);
				continue;
			}
			if (!attempt.holding) continue;

			float reach = attempt.reach();
			if (attempt.turn < reach) {
				attempt.turn = Math.min(reach, attempt.turn + TURN_PER_TICK);
				LockScreen.refresh(player, attempt);
				continue;
			}

			if (attempt.turns()) {
				if (++attempt.heldOpen >= HELD_OPEN_TICKS) opened(player, attempt);
				continue;
			}

			if (!attempt.shaking) {
				attempt.shaking = true;
				player.level().playSound(null, attempt.pos, SoundEvents.CHAIN_HIT,
					SoundSource.BLOCKS, 0.4F, 0.7F);
			}
			if (wear(player, attempt)) {
				snap(player, attempt);
				continue;
			}
			// Every tick, because the tremble is the warning: it is drawn from the wear
			LockScreen.refresh(player, attempt);
		}
	}

	/** Step away from the lock, keeping what the turns taught. */
	private static void give_up(ServerPlayer player) {
		LockAttempt attempt = release(player);
		if (attempt == null) return;

		player.level().playSound(null, attempt.pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
			SoundSource.BLOCKS, 0.5F, 1.0F);
	}

	/**
	 * Let the player go, whatever they were part way through. A pick worn past keeping goes
	 * with the screen, whichever way the screen went: opened or walked away from.
	 */
	private static LockAttempt release(ServerPlayer player) {
		LockAttempt attempt = attempts.remove(player.getUUID());
		LockScreen.close(player);
		if (attempt != null && attempt.wear >= SPENT_WEAR && consumePick(player)) {
			player.sendSystemMessage(Component.translatable("loot-ender.lock.spent"), true);
		}
		return attempt;
	}

	/** Drop everything held for a player who is gone. */
	private static void forget(UUID player) {
		attempts.remove(player);
		LockScreen.forget(player);
	}

	/**
	 * Stand between a player and a loot container, if it is locked and they can be shown a lock.
	 *
	 * @param open what to do once it is open, called now if there was no lock in the way
	 * @return true when the caller should carry on and open the container itself
	 */
	public static boolean unlocked(ServerLevel level, ServerPlayer player, BlockPos pos, long key,
			ResourceKey<LootTable> table, long seed, Consumer<ServerPlayer> open) {
		if (!LootEnderConfig.lockpicking()) return true;
		// A client without Pandorical cannot draw the lock, and would stand at the chest forever.
		// It gets no locks at all, its chests opening the way they always did.
		if (!PandoricalApi.isAvailable(player)) return true;

		LockVault vault = LockVault.get(level);
		if (vault.isPicked(player.getUUID(), key)) return true;

		LockTier tier = LockTier.on(table, pos, seed);
		if (tier == LockTier.UNLOCKED) return true;

		if (countPicks(player) == 0) {
			player.sendSystemMessage(Component.translatable("loot-ender.lock.no_picks"), true);
			player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6F, 1.0F);
			return false;
		}

		// A fresh answer every visit. It used to be seeded off the chest and the player, so that
		// a snapped pick's lesson survived a logout; but that also let a lock be probed for a
		// moment, walked away from under the wear that spends a pick, and come back to with the
		// answer known. The answer holds while you stay at the lock, snaps and all, and not past.
		begin(player, tier, LockDifficulty.of(player), pos, key, true, player.getRandom().nextInt(tier.positions), open);
		return false;
	}

	/** Whether this player is carrying anything to pick a lock with. */
	public static boolean hasPick(ServerPlayer player) {
		return countPicks(player) > 0;
	}

	/**
	 * Put a player in front of somebody else's claim on a chest, as
	 * {@link justfatlard.loot_ender.lock.PlayerLocks} decided they may be.
	 *
	 * <p>Nothing written down when it opens, which is the difference from a loot chest. A loot
	 * chest's lock is one obstacle between a player and their own copy, and asking for it twice
	 * would be pointless; a player's lock is a standing defence, and one that only ever had to
	 * be beaten once would be a lock somebody picked in March and still owned in December.
	 */
	public static void pickPlayerLock(ServerPlayer player, BlockPos pos, Consumer<ServerPlayer> open) {
		// Normal whoever is picking: an easier setting is for loot, not for somebody's claim.
		begin(player, PLAYER_CLAIM_TIER, LockDifficulty.NORMAL, pos, pos.asLong(), false,
			player.getRandom().nextInt(PLAYER_CLAIM_TIER.positions), open);
	}

	private static void begin(ServerPlayer player, LockTier tier, LockDifficulty difficulty, BlockPos pos,
			long key, boolean remembered, int sweetSpot, Consumer<ServerPlayer> open) {
		LockAttempt attempt = new LockAttempt(tier, difficulty, pos, key, remembered, sweetSpot, open);
		attempts.put(player.getUUID(), attempt);
		LockScreen.open(player, attempt);

		player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6F, 1.2F);
	}

	/**
	 * A tick of forcing against the jam: the lock's rate, eased the nearer the pick is to the
	 * answer.
	 *
	 * @return true when the pick has nothing left
	 */
	private static boolean wear(ServerPlayer player, LockAttempt attempt) {
		attempt.wear += attempt.stressRate() * (1F - attempt.closeness() * 0.6F);
		return attempt.wear >= 1F;
	}

	private static void snap(ServerPlayer player, LockAttempt attempt) {
		consumePick(player);
		attempt.wear = 0F;
		int left = countPicks(player);

		player.level().playSound(null, attempt.pos, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8F, 1.1F);

		if (left == 0) {
			release(player);
			player.sendSystemMessage(Component.translatable("loot-ender.lock.out_of_picks"), true);
			return;
		}

		// The cylinder falls back shut with the pick that was holding it, but the answer does not
		// move: what this probe taught is still true on the next one.
		attempt.holding = false;
		attempt.turn = 0F;
		attempt.heldOpen = 0;
		attempt.shaking = false;
		attempt.broken = BROKEN_TICKS;
		LockScreen.refresh(player, attempt);
	}

	private static void opened(ServerPlayer player, LockAttempt attempt) {
		release(player);
		if (attempt.remembered) LockVault.get(player.level()).remember(player.getUUID(), attempt.key);

		player.level().playSound(null, attempt.pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.6F);
		attempt.open.accept(player);
	}

	static int countPicks(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(LootEnderItems.LOCKPICK)) total += stack.getCount();
		}
		return total;
	}

	/** Take one pick, from the hand that holds one if either does, else from wherever the first is. */
	private static boolean consumePick(ServerPlayer player) {
		for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (held.is(LootEnderItems.LOCKPICK)) {
				held.shrink(1);
				return true;
			}
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(LootEnderItems.LOCKPICK)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}
}
