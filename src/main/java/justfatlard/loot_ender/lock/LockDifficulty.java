package justfatlard.loot_ender.lock;

import java.util.Locale;

import com.mojang.serialization.Codec;
import justfatlard.loot_ender.LootEnderConfig;
import justfatlard.loot_ender.Main;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

/**
 * How hard a lock is to pick, on top of how good the lock is: the server's choice, or an op's for
 * one player, so a child learning the game and a player who finds it too easy can share a world.
 */
public enum LockDifficulty implements StringRepresentable {
	/** A wide answer, a slow-wearing pick, and a turn that says plainly how near the pick is. */
	EASIEST("Easiest", 2F, 0.4F, 2F),
	EASY("Easy", 1.5F, 0.7F, 3F),
	NORMAL("Normal", 1F, 1F, 3F),
	HARD("Hard", 0.75F, 1.3F, 3F);

	public static final Codec<LockDifficulty> CODEC = StringRepresentable.fromEnum(LockDifficulty::values);

	/** Set by an op for this player, over the server's. Absent means the server's. */
	public static final AttachmentType<LockDifficulty> CHOSEN = AttachmentRegistry.<LockDifficulty>builder()
		.persistent(CODEC)
		.copyOnDeath()
		.buildAndRegister(Identifier.fromNamespaceAndPath(Main.MOD_ID, "lock_difficulty"));

	public final String label;
	/** The width of the answer, times the lock's own. */
	public final float window;
	/** How fast a pick wears, times the lock's own. */
	public final float wear;
	/** The power the pick's nearness is raised to for how far the cylinder turns: lower reads plainer. */
	public final float feedback;

	LockDifficulty(String label, float window, float wear, float feedback) {
		this.label = label;
		this.window = window;
		this.wear = wear;
		this.feedback = feedback;
	}

	/** Loads the class, which is what registers the attachment; it must happen at init. */
	public static void init() {}

	public static LockDifficulty of(ServerPlayer player) {
		LockDifficulty chosen = player.getAttached(CHOSEN);
		return chosen != null ? chosen : LootEnderConfig.lockDifficulty();
	}

	/** Null clears it, back to the server's. */
	public static void choose(ServerPlayer player, LockDifficulty difficulty) {
		if (difficulty == null) player.removeAttached(CHOSEN);
		else player.setAttached(CHOSEN, difficulty);
	}

	public static LockDifficulty chosen(ServerPlayer player) {
		return player.getAttached(CHOSEN);
	}

	@Override
	public String getSerializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Null for anything that is not one of them. */
	public static LockDifficulty named(String name) {
		for (LockDifficulty difficulty : values()) {
			if (difficulty.getSerializedName().equals(name.trim().toLowerCase(Locale.ROOT))) return difficulty;
		}
		return null;
	}
}
