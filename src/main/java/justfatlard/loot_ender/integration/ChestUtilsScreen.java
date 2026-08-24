package justfatlard.loot_ender.integration;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

/**
 * Shows a player's loot copy through chest-utils, when it is installed.
 *
 * <p>What that buys is the two buttons that suit a loot chest exactly: take the lot, or top up
 * the stacks already in your pack from it. Standing in front of a chest deciding which of forty
 * slots is worth a click is the least interesting part of finding one.
 *
 * <p>Reached by name rather than by importing it, so loot-ender neither compiles nor runs against
 * chest-utils: without it the lookup fails once, is remembered as absent, and every copy opens
 * the plain way it always did.
 */
public final class ChestUtilsScreen {
	private ChestUtilsScreen() {}

	private static final String CLASS = "justfatlard.chest_utils.screen.ChestScreens";

	/** Resolved on first use and then remembered, including the failure. */
	private static boolean looked = false;
	private static Method open = null;

	public static boolean show(ServerPlayer player, Container container, Component title, int rows) {
		Method method = resolve();
		if (method == null) return false;

		try {
			method.invoke(null, player, container, title, rows);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			// Once is a mishap; every open after this one goes the plain way.
			open = null;
			return false;
		}
	}

	private static synchronized Method resolve() {
		if (looked) return open;
		looked = true;

		if (!FabricLoader.getInstance().isModLoaded("chest-utils")) return null;

		try {
			open = Class.forName(CLASS).getMethod(
				"openTakeOnly", ServerPlayer.class, Container.class, Component.class, int.class);
		} catch (ReflectiveOperationException e) {
			open = null;
		}
		return open;
	}
}
