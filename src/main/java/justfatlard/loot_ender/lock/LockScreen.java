package justfatlard.loot_ender.lock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import justfatlard.loot_ender.LootEnderItems;
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
 * out. Nothing is framed. The world darkens behind, and in the middle of it is the chest
 * itself - its front cut from the same sheet the block is painted with, blown up until it
 * fills the view - with the latch over the seam grown to a lock plate, so the thing being
 * picked still looks like the thing that was clicked. The picks you have left sit in one
 * corner, the lock's grade in the other, and the controls run along the bottom.
 *
 * <p>Three sprites share the plate's square and its centre, so turning them turns them in
 * place: the plate never moves, the plug shows how far the turn has got, and the pick is a
 * dial - swept round the keyhole on the client's own clock, pushed to turn, and turned with the
 * plug when the plug turns, because it is sitting in it. There were buttons for left, right
 * and turn once, and a lock worked a notch per click read as a form to fill in rather than a
 * thing in the hand.
 */
public final class LockScreen {
	private LockScreen() {}

	public static final String TYPE = "loot-ender:lock";

	public static final String PICK = "pick";

	private static final int WIDTH = 260;
	/**
	 * The game never scales its interface below 240 tall, and this must clear the hotbar drawn
	 * along the bottom of that: what fits on a small laptop fits everywhere.
	 */
	private static final int HEIGHT = 196;

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
	private static final int SCALE = 11;
	private static final int CHEST_SIZE = FRONT_COLS * SCALE;
	private static final int CHEST_X = (WIDTH - CHEST_SIZE) / 2;
	/** Under the corner captions and clear of the hints: the chest takes everything between. */
	private static final int CHEST_Y = 14;
	/** Where the lid meets the base, which is where the latch sits on the block. */
	private static final int SEAM_Y = CHEST_Y + LID_ROWS * SCALE;

	private static final String PLATE_SHEET = "loot-ender:textures/gui/lock_face.png";
	private static final String CYLINDER_SHEET = "loot-ender:textures/gui/lock_cylinder.png";
	private static final String PICK_SHEET = "loot-ender:textures/gui/lock_pick.png";
	private static final String PICK_BROKEN_SHEET = "loot-ender:textures/gui/lock_pick_broken.png";
	private static final int PLATE_SIZE = 128;
	private static final int PLATE_X = (WIDTH - PLATE_SIZE) / 2;
	private static final int PLATE_Y = SEAM_Y - PLATE_SIZE / 2;

	/** The arc the pick sweeps across the lock, in degrees, from one end of the cylinder to the other. */
	static final float SWEEP = 140F;
	/** How far the cylinder turns when it turns all the way. */
	private static final float FULL_TURN = 90F;

	private static final String TITLE_COLOR = "#F0E6D2";
	private static final String COUNT_COLOR = "#FFFFFF";
	private static final String SNAPPED_COLOR = "#FF5555";
	private static final String HINT_COLOR = "#8C8C8C";
	private static final String WEAR_SLOT_COLOR = "#FF1B1B1B";

	private static final int WEAR_X = 8;
	private static final int WEAR_Y = 20;
	private static final int WEAR_WIDTH = 34;
	private static final int WEAR_HEIGHT = 2;

	private static final Map<UUID, String> open = new ConcurrentHashMap<>();

	public static void open(ServerPlayer player, LockAttempt attempt) {
		ScreenBuilder screen = new ScreenBuilder(TYPE)
			.size(WIDTH, HEIGHT)
			.title("Lock");

		screen.itemIcon("pick_icon", 6, 2, LootEnderItems.LOCKPICK_ID.toString(), 1);
		screen.component(new ComponentBuilder("picks", ComponentType.TEXT)
			.bounds(26, 6, 40, 10)
			.prop(ComponentType.PROP_TEXT, picks(player))
			.prop(ComponentType.PROP_COLOR, COUNT_COLOR)
			.prop(ComponentType.PROP_SHADOW, "true"));
		// The pick's wear, drawn the way the game draws wear under a tool: a dark slot with a
		// bar in it that shortens and reddens. Under the icon and the count, so it reads as theirs.
		screen.component(new ComponentBuilder("wear_slot", ComponentType.SPRITE)
			.bounds(WEAR_X - 1, WEAR_Y - 1, WEAR_WIDTH + 2, WEAR_HEIGHT + 2)
			.prop(ComponentType.PROP_COLOR, WEAR_SLOT_COLOR));
		screen.component(new ComponentBuilder("wear", ComponentType.SPRITE)
			.bounds(WEAR_X, WEAR_Y, WEAR_WIDTH, WEAR_HEIGHT)
			.prop(ComponentType.PROP_COLOR, wearColor(attempt)));
		screen.component(new ComponentBuilder("title", ComponentType.TEXT)
			.bounds(WIDTH / 2, 6, WIDTH / 2 - 8, 10)
			.prop(ComponentType.PROP_TEXT_KEY, attempt.tier.nameKey)
			.prop(ComponentType.PROP_COLOR, TITLE_COLOR)
			.prop(ComponentType.PROP_SHADOW, "true")
			.prop(ComponentType.PROP_ALIGN, "right"));

		// Base under lid, as on the block, so the lid's bottom edge is what shows at the seam.
		screen.component(chestPart("base", BASE_V, BASE_ROWS,
			CHEST_Y + (LID_ROWS - LID_OVERLAP) * SCALE));
		screen.component(chestPart("lid", LID_V, LID_ROWS, CHEST_Y));

		screen.component(plateLayer("plate", PLATE_SHEET, 0F));
		screen.component(plateLayer("cylinder", CYLINDER_SHEET, cylinderDegrees(attempt)));
		screen.component(new ComponentBuilder(PICK, ComponentType.DIAL)
			.bounds(PLATE_X, PLATE_Y, PLATE_SIZE, PLATE_SIZE)
			.rotation(cylinderDegrees(attempt))
			.prop(ComponentType.PROP_TEXTURE, PICK_SHEET)
			.prop(ComponentType.PROP_SWEEP, String.valueOf(SWEEP)));

		screen.component(centred("hint_pick", HEIGHT - 24, Map.of(
			ComponentType.PROP_TEXT_KEY, "loot-ender.lock.hint.pick",
			ComponentType.PROP_COLOR, HINT_COLOR,
			ComponentType.PROP_SHADOW, "true")));
		screen.component(centred("hint_turn", HEIGHT - 12, Map.of(
			ComponentType.PROP_TEXT_KEY, "loot-ender.lock.hint.turn",
			ComponentType.PROP_COLOR, HINT_COLOR,
			ComponentType.PROP_SHADOW, "true")));

		open.put(player.getUUID(), screen.screenId());
		PandoricalApi.screens().open(player, screen.build());
	}

	/**
	 * Show how far the cylinder has got, the pick going round with it and trembling by how
	 * worn it is, or lying snapped; the wear on the bar in the corner; and what is left, in
	 * red for a moment when one has just gone.
	 */
	public static void refresh(ServerPlayer player, LockAttempt attempt) {
		String id = open.get(player.getUUID());
		if (id == null) return;

		String degrees = String.valueOf(cylinderDegrees(attempt));
		boolean snapped = attempt.broken > 0;
		PandoricalApi.screens().update(player, id, List.of(
			new ComponentUpdate(PICK, Map.of(
				ComponentType.PROP_ROTATION, degrees,
				ComponentType.PROP_TEXTURE, snapped ? PICK_BROKEN_SHEET : PICK_SHEET,
				ComponentType.PROP_SHAKE, String.valueOf(shake(attempt)))),
			new ComponentUpdate("cylinder", Map.of(ComponentType.PROP_ROTATION, degrees)),
			new ComponentUpdate("wear", Map.of(
				ComponentType.PROP_WIDTH, String.valueOf(wearWidth(attempt)),
				ComponentType.PROP_COLOR, wearColor(attempt))),
			new ComponentUpdate("picks", Map.of(
				ComponentType.PROP_TEXT, picks(player),
				ComponentType.PROP_COLOR, snapped ? SNAPPED_COLOR : COUNT_COLOR))));
	}

	public static void close(ServerPlayer player) {
		String id = open.remove(player.getUUID());
		if (id != null) PandoricalApi.screens().close(player, id);
	}

	public static void forget(UUID player) {
		open.remove(player);
	}

	private static String picks(ServerPlayer player) {
		return "x " + Lockpicking.countPicks(player);
	}

	/** A jammed pick trembles from the moment it jams, and worse the more worn it is. */
	private static float shake(LockAttempt attempt) {
		if (!attempt.shaking) return 0F;
		return 0.2F + 0.8F * Math.clamp(attempt.wear, 0F, 1F);
	}

	private static float remaining(LockAttempt attempt) {
		return Math.clamp(1F - attempt.wear, 0F, 1F);
	}

	private static int wearWidth(LockAttempt attempt) {
		return Math.round(WEAR_WIDTH * remaining(attempt));
	}

	/** Green to red as it goes, on the same hue walk vanilla's durability bar takes. */
	private static String wearColor(LockAttempt attempt) {
		int rgb = net.minecraft.util.Mth.hsvToRgb(remaining(attempt) / 3F, 1F, 1F);
		return String.format("#%06X", rgb & 0xFFFFFF);
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
