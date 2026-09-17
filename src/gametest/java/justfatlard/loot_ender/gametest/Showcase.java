package justfatlard.loot_ender.gametest;

import justfatlard.loot_ender.LootMarks;
import justfatlard.loot_ender.lock.Lockpicking;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.List;

/**
 * The pictures for the readme and the mod page: loot chests wearing their clasps, and the lock a
 * masterwork chest puts between a player and their copy.
 *
 * <p>The lock is opened through the mod's own {@code Lockpicking.unlocked}, the call a chest makes
 * when somebody reaches for it, so the screen in the picture is the screen a player meets. Run it
 * under xvfb-run; the frames land in build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("time set noon");
			server.runCommand("gamemode creative @a");
			// Said early, so the line about it has faded out of the chat before the lock is shown:
			// the chat sits exactly where the lock's own instructions are.
			// A lock is only ever shown to somebody carrying something to pick it with.
			server.runCommand("give @a loot-ender:lockpick 3");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int y = origin.getY();
			int z = origin.getZ();
			BlockPos chest = new BlockPos(x, y, z - 4);

			// A stone room with three chests in it, each carrying loot worth locking.
			server.runCommand("fill %d %d %d %d %d %d minecraft:stone_bricks"
				.formatted(x - 5, y - 1, z - 7, x + 5, y + 4, z + 3));
			server.runCommand("fill %d %d %d %d %d %d minecraft:air"
				.formatted(x - 4, y, z - 6, x + 4, y + 3, z + 2));
			server.runCommand("setblock %d %d %d minecraft:lantern[hanging=true]"
				.formatted(x, y + 3, z - 2));
			for (int i = -1; i <= 1; i++) {
				server.runCommand("setblock %d %d %d minecraft:chest[facing=south]{LootTable:\"%s\"}"
					.formatted(x + i * 2, y, z - 4, "minecraft:chests/stronghold_library"));
			}

			// The clasps are handed out as loot chests load, and these were set into a chunk that
			// was already loaded, so they are handed out here instead - the same call, by name.
			server.runOnServer(s -> LootMarks.noticed(connection.getServerPlayer(), List.of(),
				List.of(chest.west(2), chest, chest.east(2))));

			context.getInput().pressKey(options -> options.keyToggleGui);
			look(server, x + 0.5, y, z + 0.5, chest.getX() + 0.5, y + 0.6, chest.getZ() + 0.5);
			context.waitTicks(40);
			shoot(context, "loot-chests");

			// And the lock itself, through the call a chest makes when somebody reaches for it.
			context.getInput().pressKey(options -> options.keyToggleGui);
			context.waitTicks(220);
			server.runOnServer(s -> Lockpicking.unlocked(s.overworld(), connection.getServerPlayer(),
				chest, chest.asLong(), BuiltInLootTables.STRONGHOLD_LIBRARY, 0L, player -> { }));
			context.waitTicks(40);
			shoot(context, "lock");
		}
	}

	/**
	 * Stand the camera at one place and point it at another. The camera's y is the feet, so it
	 * looks from 1.62 above where it stands.
	 */
	private void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
