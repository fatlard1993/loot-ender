package justfatlard.loot_ender.lock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.ScreenBuilder;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.level.ServerPlayer;

/**
 * The lock, drawn on the front of the chest it is on.
 *
 * <p>A screen, because a screen is the one thing the game will hold a player still for: while
 * it is up the mouse and the keys are its and not the world's, and closing it is the only way
 * out. What it shows is the chest itself - its front cut from the same sheet the block is
 * painted with, blown up until it fills the view - with the lock plate over the latch, so the
 * thing being picked still looks like the thing that was clicked.
 *
 * <p>Three sprites share the plate's square and its centre, so turning them turns them in
 * place: the face never moves, the cylinder shows how far the last turn got, and the pick is a
 * dial - it follows the mouse round the keyhole on the client's own clock, and reports back.
 * There were buttons for left, right and turn once, and a lock worked a notch per click read
 * as a form to fill in rather than a thing in the hand.
 */
public final class LockScreen {
	private LockScreen() {}

	public static final String TYPE = "loot-ender:lock";

	public static final String PICK = "pick";
	public static final String LEAVE = "leave";

	private static final int WIDTH = 176;
	private static final int HEIGHT = 192;

	/** The chest's own sheet, so the front here is the front out there to the pixel. */
	private static final String CHEST_SHEET = "minecraft:textures/entity/chest/normal.png";
	private static final int SHEET_SIZE = 64;
	/** Where the front face sits on the sheet: the one strip with the latch notch cut into it. */
	private static final int FRONT_U = 42;
	private static final int FRONT_COLS = 14;
	private static final int LID_V = 14;
	private static final int LID_ROWS = 5;
	private static final int BASE_V = 33;
	private static final int BASE_ROWS = 10;
	/** The lid comes down over the base's top row, as it does on the block. */
	private static final int LID_OVERLAP = 1;

	/**
	 * Screen pixels per chest pixel. Odd, so a sprite scaled about its centre lands on whole
	 * pixels: the client grows a sprite from the middle of its native bounds, and an even
	 * factor on an odd height puts every edge half a pixel off.
	 */
	private static final int SCALE = 7;
	private static final int CHEST_SIZE = FRONT_COLS * SCALE;
	private static final int CHEST_X = (WIDTH - CHEST_SIZE) / 2;
	private static final int CHEST_Y = 20;

	private static final String PLATE_SHEET = "loot-ender:textures/gui/lock_face.png";
	private static final String CYLINDER_SHEET = "loot-ender:textures/gui/lock_cylinder.png";
	private static final String PICK_SHEET = "loot-ender:textures/gui/lock_pick.png";
	private static final int PLATE_SIZE = 64;
	private static final int PLATE_X = CHEST_X + (CHEST_SIZE - PLATE_SIZE) / 2;
	private static final int PLATE_Y = CHEST_Y + (CHEST_SIZE - PLATE_SIZE) / 2;

	/** The arc the pick sweeps across the lock, in degrees, from one end of the cylinder to the other. */
	static final float SWEEP = 140F;
	/** How far the cylinder turns when it turns all the way. */
	private static final float FULL_TURN = 90F;

	private static final String LABEL_COLOR = "#404040";
	private static final String HINT_COLOR = "#707070";

	private static final Map<UUID, String> open = new ConcurrentHashMap<>();

	public static void open(ServerPlayer player, LockAttempt attempt) {
		ScreenBuilder screen = new ScreenBuilder(TYPE)
			.size(WIDTH, HEIGHT)
			.title("Lock");

		screen.panel("frame", 0, 0, WIDTH, HEIGHT, Map.of());
		screen.component(centred("title", 7, Map.of(
			ComponentType.PROP_TEXT_KEY, attempt.tier.nameKey,
			ComponentType.PROP_COLOR, LABEL_COLOR)));

		// Base under lid, as on the block, so the lid's bottom edge is what shows at the seam.
		screen.component(chestPart("base", BASE_V, BASE_ROWS,
			CHEST_Y + (LID_ROWS - LID_OVERLAP) * SCALE));
		screen.component(chestPart("lid", LID_V, LID_ROWS, CHEST_Y));

		screen.component(plateLayer("plate", PLATE_SHEET, 0F));
		screen.component(plateLayer("cylinder", CYLINDER_SHEET, cylinderDegrees(attempt)));
		screen.component(new ComponentBuilder(PICK, ComponentType.DIAL)
			.bounds(PLATE_X, PLATE_Y, PLATE_SIZE, PLATE_SIZE)
			.prop(ComponentType.PROP_TEXTURE, PICK_SHEET)
			.prop(ComponentType.PROP_SWEEP, String.valueOf(SWEEP)));

		screen.component(centred("status", CHEST_Y + CHEST_SIZE + 6, Map.of(
			ComponentType.PROP_TEXT, status(player, attempt),
			ComponentType.PROP_COLOR, LABEL_COLOR)));
		screen.component(new ComponentBuilder("hint", ComponentType.TEXT)
			.bounds(8, CHEST_Y + CHEST_SIZE + 20, WIDTH - 16, 20)
			.prop(ComponentType.PROP_TEXT_KEY, "loot-ender.lock.hint")
			.prop(ComponentType.PROP_COLOR, HINT_COLOR)
			.prop(ComponentType.PROP_WRAP_WIDTH, String.valueOf(WIDTH - 16))
			.prop(ComponentType.PROP_ALIGN, "center"));

		screen.button(LEAVE, (WIDTH - 60) / 2, HEIGHT - 28, 60, 20, Map.of(ComponentType.PROP_LABEL_KEY, "loot-ender.lock.leave"));

		open.put(player.getUUID(), screen.screenId());
		PandoricalApi.screens().open(player, screen.build());
	}

	/** Show where the pick is now, how far the cylinder got, and what is left to snap. */
	public static void refresh(ServerPlayer player, LockAttempt attempt) {
		String id = open.get(player.getUUID());
		if (id == null) return;

		PandoricalApi.screens().update(player, id, List.of(
			new ComponentUpdate(PICK, Map.of(ComponentType.PROP_SHAKE, String.valueOf(attempt.shaking))),
			new ComponentUpdate("cylinder", Map.of(ComponentType.PROP_ROTATION, String.valueOf(cylinderDegrees(attempt)))),
			new ComponentUpdate("status", Map.of(ComponentType.PROP_TEXT, status(player, attempt)))));
	}

	public static void close(ServerPlayer player) {
		String id = open.remove(player.getUUID());
		if (id != null) PandoricalApi.screens().close(player, id);
	}

	public static void forget(UUID player) {
		open.remove(player);
	}

	private static String status(ServerPlayer player, LockAttempt attempt) {
		return "Picks left " + Lockpicking.countPicks(player);
	}

	private static float cylinderDegrees(LockAttempt attempt) {
		return attempt.turn * FULL_TURN;
	}

	/**
	 * One strip of the chest front, drawn at its native size and grown by {@link #SCALE} about
	 * its centre - which is why its corner is set in from where the strip should land by half
	 * the growth.
	 */
	private static ComponentBuilder chestPart(String id, int v, int rows, int y) {
		int inset = (SCALE - 1) / 2;
		return new ComponentBuilder(id, ComponentType.SPRITE)
			.bounds(CHEST_X + inset * FRONT_COLS, y + inset * rows, FRONT_COLS, rows)
			.scale(SCALE)
			.prop(ComponentType.PROP_TEXTURE, CHEST_SHEET)
			.prop(ComponentType.PROP_TEXTURE_WIDTH, String.valueOf(SHEET_SIZE))
			.prop(ComponentType.PROP_TEXTURE_HEIGHT, String.valueOf(SHEET_SIZE))
			.prop(ComponentType.PROP_TEXTURE_U, String.valueOf(FRONT_U))
			.prop(ComponentType.PROP_TEXTURE_V, String.valueOf(v));
	}

	private static ComponentBuilder plateLayer(String id, String sheet, float degrees) {
		return new ComponentBuilder(id, ComponentType.SPRITE)
			.bounds(PLATE_X, PLATE_Y, PLATE_SIZE, PLATE_SIZE)
			.rotation(degrees)
			.prop(ComponentType.PROP_TEXTURE, sheet);
	}

	private static ComponentBuilder centred(String id, int y, Map<String, String> props) {
		return new ComponentBuilder(id, ComponentType.TEXT)
			.bounds(0, y, WIDTH, 10)
			.props(props)
			.prop(ComponentType.PROP_ALIGN, "center");
	}
}
