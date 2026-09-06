package justfatlard.loot_ender.integration;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads chest-utils' player locks, and opens a chest once one has been picked.
 *
 * <p>Reached by name rather than by importing it, the same way {@link ChestUtilsScreen} is, so
 * loot-ender neither compiles nor runs against chest-utils: without it every lookup here fails
 * once, is remembered as absent, and player locks simply are not a thing this mod has heard of.
 *
 * <p>Opening goes through chest-utils' own four-argument screen deliberately. The longer one
 * carries the lock and dye controls, and handing those to somebody who just picked their way in
 * would let a thief relock the chest behind them.
 */
public final class ChestUtilsLocks {
	private ChestUtilsLocks() {}

	private static final String LOCKS = "justfatlard.chest_utils.block.ChestLocks";
	private static final String SCREENS = "justfatlard.chest_utils.screen.ChestScreens";

	private static boolean looked = false;
	private static Method get;
	private static Method refuses;
	private static Method lockedBy;
	private static Method halvesOf;
	private static Method lockAt;
	private static Method owner;
	private static Method shared;
	private static Method shareId;
	private static Method open;

	/** Whether chest-utils is here and says this chest is shut against this player. */
	public static boolean refuses(ServerLevel level, ServerPlayer player, BlockState state, BlockPos pos) {
		if (!resolve()) return false;

		try {
			return (boolean) refuses.invoke(get.invoke(null, level), player, state, pos);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return failed();
		}
	}

	/** The owner's name, for telling a picker whose chest they are standing at. */
	public static String lockedBy(ServerLevel level, BlockState state, BlockPos pos) {
		if (!resolve()) return null;

		try {
			return (String) lockedBy.invoke(get.invoke(null, level), state, pos);
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed();
			return null;
		}
	}

	/**
	 * Everyone the lock lets in: the owner and anybody they have shared it with.
	 *
	 * <p>All of them, because a chest is only abandoned when everybody who could open it has
	 * stopped turning up. An owner away for a year whose housemate was on this morning is not
	 * somebody's forgotten chest, it is somebody's chest.
	 */
	public static List<UUID> keyholders(ServerLevel level, BlockState state, BlockPos pos) {
		if (!resolve()) return List.of();

		try {
			Object locks = get.invoke(null, level);
			List<UUID> holders = new ArrayList<>();

			for (Object half : (List<?>) halvesOf.invoke(null, state, pos)) {
				Object lock = lockAt.invoke(locks, half);
				if (lock == null) continue;

				holders.add((UUID) owner.invoke(lock));
				for (Object share : (List<?>) shared.invoke(lock)) {
					holders.add((UUID) shareId.invoke(share));
				}
			}
			return holders;
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed();
			return List.of();
		}
	}

	/** Hand the picked chest over on chest-utils' plain buttoned screen. */
	public static boolean open(ServerPlayer player, Container container, Component title, int rows) {
		if (!resolve()) return false;

		try {
			open.invoke(null, player, container, title, rows);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return failed();
		}
	}

	/** One mishap and the whole bridge goes quiet, rather than throwing at every chest. */
	private static boolean failed() {
		looked = true;
		get = null;
		return false;
	}

	private static synchronized boolean resolve() {
		if (looked) return get != null;
		looked = true;

		if (!FabricLoader.getInstance().isModLoaded("chest-utils")) return false;

		try {
			Class<?> locks = Class.forName(LOCKS);
			Class<?> lock = Class.forName(LOCKS + "$Lock");
			Class<?> share = Class.forName(LOCKS + "$Share");

			get = locks.getMethod("get", ServerLevel.class);
			refuses = locks.getMethod("refuses", ServerPlayer.class, BlockState.class, BlockPos.class);
			lockedBy = locks.getMethod("lockedBy", BlockState.class, BlockPos.class);
			halvesOf = locks.getMethod("halvesOf", BlockState.class, BlockPos.class);
			lockAt = locks.getMethod("lockAt", BlockPos.class);
			owner = lock.getMethod("owner");
			shared = lock.getMethod("shared");
			shareId = share.getMethod("id");
			open = Class.forName(SCREENS).getMethod(
				"open", ServerPlayer.class, Container.class, Component.class, int.class);
			return true;
		} catch (ReflectiveOperationException e) {
			// Said out loud, unlike the screen bridge next door. That one going quiet costs two
			// buttons; this one going quiet means a server that deliberately turned player locks
			// on gets a setting that does nothing, and nothing anywhere saying why.
			justfatlard.loot_ender.Main.LOGGER.warn(
				"[{}] chest-utils is installed but its locks do not look the way this expects ({});"
					+ " player locks stay unpickable", justfatlard.loot_ender.Main.MOD_ID, e.toString());
			get = null;
			return false;
		}
	}
}
