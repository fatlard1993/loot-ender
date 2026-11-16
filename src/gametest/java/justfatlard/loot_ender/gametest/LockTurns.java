package justfatlard.loot_ender.gametest;

import java.lang.reflect.Field;
import java.util.Map;

import justfatlard.loot_ender.lock.Lockpicking;
import justfatlard.pandorical.api.Capabilities;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * A pushed pick turns the cylinder as far as the pick's place lets it, half a second all the way
 * round. A client that can turn it itself is told once where it stops and how fast, and turns it
 * there at the server's pace without being sent a step a tick; let go, it springs back.
 */
public final class LockTurns implements FabricClientGameTest {
	/** {@code Lockpicking.TURN_PER_TICK} of the lock's quarter turn. */
	private static final float RATE = 9F;
	/** Ten ticks all the way round, and a couple for the push to reach the server. */
	private static final int CLIMB = 14;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));
			check(server.computeOnServer(s -> PandoricalApi.hasCapability(connection.getServerPlayer(), Capabilities.DECLARED_MOTION)),
				"the client does not declare declared_motion");

			// The gentlest wear, so a jam held for the length of the test cannot snap the pick.
			server.runCommand("lockpicking difficulty server easiest");
			try {
				turns(context, server, connection);
			} finally {
				server.runCommand("lockpicking difficulty server normal");
			}
		}
	}

	private static void turns(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runCommand("give @a loot-ender:lockpick 3");
		BlockPos chest = server.computeOnServer(s -> connection.getServerPlayer().blockPosition().north(2));
		server.runOnServer(s -> Lockpicking.unlocked(s.overworld(), connection.getServerPlayer(),
			chest, chest.asLong(), BuiltInLootTables.SIMPLE_DUNGEON, 0L, player -> { }));
		context.waitFor(client -> lockShown(client));
		context.waitTicks(5);
		check(context.computeOnClient(client -> drawn(client)) == 0F, "the cylinder was turned before the pick was pushed");

		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(client -> prop(client, ComponentType.PROP_TURN_RATE) != 0F);
		float target = context.computeOnClient(client -> prop(client, ComponentType.PROP_ROTATION));
		check(context.computeOnClient(client -> prop(client, ComponentType.PROP_TURN_RATE)) == RATE,
			"the cylinder was not told the lock's own pace");
		check(target > 0F && target <= 90F, "the cylinder was sent somewhere a push cannot take it: " + target);

		float last = context.computeOnClient(client -> drawn(client));
		for (int tick = 0; tick < CLIMB; tick++) {
			context.waitTick();
			if (context.computeOnClient(client -> !lockShown(client))) {
				check(target == 90F, "the lock closed on a cylinder that had not gone all the way round");
				return;
			}
			check(context.computeOnClient(client -> prop(client, ComponentType.PROP_ROTATION)) == target,
				"where the cylinder stops was sent again while it was on its way");
			float now = context.computeOnClient(client -> drawn(client));
			check(now >= last && now - last <= RATE + 0.01F,
				"the cylinder did not turn at the lock's pace: " + last + " to " + now + " in a tick");
			last = now;
		}
		check(last == target, "the cylinder did not come to rest where it was sent: " + last + ", not " + target);
		if (target == 90F) return;

		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(client -> prop(client, ComponentType.PROP_ROTATION) == 0F
			&& prop(client, ComponentType.PROP_TURN_RATE) == 0F);
		context.waitTicks(4);
		check(context.computeOnClient(client -> drawn(client)) == 0F, "the cylinder did not spring back when let go");
	}

	/*
	 * Pandorical's client classes are not on the game tests' compile path, only at run time, so the
	 * open screen and its cylinder are reached by name.
	 */
	private static final String SCREEN = "justfatlard.pandorical.client.screen.PandoricalScreen";
	private static final String COMPONENT = "justfatlard.pandorical.client.component.AbstractComponent";

	private static boolean lockShown(Minecraft client) {
		Object screen = client.gui.screen();
		return screen != null && screen.getClass().getName().equals(SCREEN);
	}

	private static float drawn(Minecraft client) {
		try {
			Object cylinder = cylinder(client);
			Object geometry = Class.forName(COMPONENT).getMethod("interpolatedGeometry", float.class).invoke(cylinder, 0F);
			return (float) geometry.getClass().getMethod("rotation").invoke(geometry);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static float prop(Minecraft client, String key) {
		try {
			Map<String, String> props = (Map<String, String>) field(Class.forName(COMPONENT), "props").get(cylinder(client));
			String value = props.get(key);
			return value == null ? 0F : Float.parseFloat(value);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Object cylinder(Minecraft client) throws ReflectiveOperationException {
		Object screen = client.gui.screen();
		Object components = field(Class.forName(SCREEN), "components").get(screen);
		Map<String, ?> index = (Map<String, ?>) field(components.getClass(), "componentIndex").get(components);
		return index.get("cylinder");
	}

	private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
