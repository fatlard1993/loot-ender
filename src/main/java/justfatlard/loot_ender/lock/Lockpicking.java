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
 * <p>Played by hand, in real time, the way the good ones are: the pick follows the mouse round
 * the keyhole, and holding the button turns the cylinder as far as the pick's position lets it.
 * On the answer it goes all the way round and the lock opens. Beside the answer it goes most of
 * the way and jams; further out, less; and a pick held against a jam trembles and, after a
 * moment, snaps. How far it turned before it jammed is the only readout there is, and working
 * the answer out from it is the game. Nothing is ever taken away for failing, only picks, and
 * the lock keeps its answer between attempts so what a snap taught you is still true.
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

	public static void registerHandlers() {
		var screens = PandoricalApi.screens();
		screens.onAction(LockScreen.TYPE, LockScreen.PICK, Lockpicking::handled);
		screens.onAction(LockScreen.TYPE, LockScreen.LEAVE, (player, data) -> give_up(player));
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
				attempt.angle = angle;
				attempt.holding = true;
				attempt.heldOpen = 0;
				player.level().playSound(null, attempt.pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM,
					SoundSource.BLOCKS, 0.5F, 0.8F + attempt.closeness() * 0.6F);
			}
			case "release" -> letGo(player, attempt);
			default -> {
				// The pick cannot move while it is turning; a report that says otherwise is stale.
				if (!attempt.holding) attempt.angle = angle;
			}
		}
	}

	/** The hand comes off: the cylinder springs back, and most of the wear on the pick goes with it. */
	private static void letGo(ServerPlayer player, LockAttempt attempt) {
		if (!attempt.holding) return;
		attempt.holding = false;
		attempt.turn = 0F;
		attempt.heldOpen = 0;
		attempt.stress *= 0.5F;
		LockScreen.refresh(player, attempt);
	}

	/** Every tick, every hand on a lock: the cylinder goes where the pick lets it, and the pick wears against a jam. */
	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, LockAttempt> held : List.copyOf(attempts.entrySet())) {
			LockAttempt attempt = held.getValue();
			if (!attempt.holding) continue;
			ServerPlayer player = server.getPlayerList().getPlayer(held.getKey());
			if (player == null) continue;
			float reach = attempt.reach();
			boolean changed = Math.abs(reach - attempt.turn) > 0.001F;
			attempt.turn = reach;
			if (attempt.turns()) {
				if (++attempt.heldOpen >= HELD_OPEN_TICKS) {
					opened(player, attempt);
					continue;
				}
			} else {
				attempt.stress += attempt.tier.stressRate * (1F - attempt.closeness() * 0.6F);
				if (attempt.stress >= 1F) {
					snap(player, attempt);
					continue;
				}
			}
			boolean shaking = !attempt.turns();
			if (changed || shaking != attempt.shaking) {
				attempt.shaking = shaking;
				LockScreen.refresh(player, attempt);
			}
		}
	}

	/** Step away from the lock, keeping what the turns taught. */
	private static void give_up(ServerPlayer player) {
		LockAttempt attempt = release(player);
		if (attempt == null) return;

		player.level().playSound(null, attempt.pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
			SoundSource.BLOCKS, 0.5F, 1.0F);
	}

	/** Let the player go, whatever they were part way through. */
	private static LockAttempt release(ServerPlayer player) {
		LockAttempt attempt = attempts.remove(player.getUUID());
		LockScreen.close(player);
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

		LockVault vault = LockVault.get(level);
		if (vault.isPicked(player.getUUID(), key)) return true;

		LockTier tier = LockTier.on(table, pos, seed);
		if (tier == LockTier.UNLOCKED) return true;

		if (countPicks(player) == 0) {
			player.sendSystemMessage(Component.translatable("loot-ender.lock.no_picks"), true);
			player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6F, 1.0F);
			return false;
		}

		// Seeded off the chest and the player, so the answer survives a snapped pick, a logout and
		// a restart, and so two players do not learn one lock between them.
		long mixed = seed * 31L + key * 17L + player.getUUID().getLeastSignificantBits();
		begin(player, tier, pos, key, true, Math.floorMod((int) (mixed ^ (mixed >>> 32)), tier.positions), open);
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
	 * <p>A fresh answer every time and nothing written down when it opens, which is the whole
	 * difference from a loot chest. A loot chest's lock is one obstacle between a player and
	 * their own copy, and asking for it twice would be pointless; a player's lock is a standing
	 * defence, and one that only ever had to be beaten once would be a lock somebody picked in
	 * March and still owned in December.
	 */
	public static void pickPlayerLock(ServerPlayer player, BlockPos pos, Consumer<ServerPlayer> open) {
		begin(player, PLAYER_CLAIM_TIER, pos, pos.asLong(), false,
			player.getRandom().nextInt(PLAYER_CLAIM_TIER.positions), open);
	}

	private static void begin(ServerPlayer player, LockTier tier, BlockPos pos, long key,
			boolean remembered, int sweetSpot, Consumer<ServerPlayer> open) {
		LockAttempt attempt = new LockAttempt(tier, pos, key, remembered, sweetSpot, open);
		attempts.put(player.getUUID(), attempt);
		LockScreen.open(player, attempt);

		player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6F, 1.2F);
	}

	private static void snap(ServerPlayer player, LockAttempt attempt) {
		consumePick(player);
		int left = countPicks(player);

		player.level().playSound(null, attempt.pos, SoundEvents.ITEM_FRAME_BREAK, SoundSource.BLOCKS, 0.7F, 1.4F);

		if (left == 0) {
			release(player);
			player.sendSystemMessage(Component.translatable("loot-ender.lock.out_of_picks"), true);
			return;
		}

		// The cylinder falls back shut with the pick that was holding it, but the answer does not
		// move: what this probe taught is still true on the next one.
		attempt.holding = false;
		attempt.turn = 0F;
		attempt.stress = 0F;
		attempt.heldOpen = 0;
		attempt.shaking = false;
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

	private static void consumePick(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(LootEnderItems.LOCKPICK)) {
				stack.shrink(1);
				return;
			}
		}
	}
}
