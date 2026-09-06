package justfatlard.loot_ender.lock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * How long ago a player was last on this server.
 *
 * <p>Read off the timestamp on their save file rather than kept in a ledger of our own, because a
 * ledger would only know about players seen since this feature was installed - and the whole
 * question it answers is about the ones who left before that.
 *
 * <p>The file is rewritten whenever a player is saved, which includes every logout and the
 * periodic autosave while they are online, so its age is their absence. An online player is
 * checked outright rather than trusted to the file, since an autosave can be a while apart.
 */
public final class LastSeen {
	private LastSeen() {}

	/**
	 * Whether this player has been away for at least this many days.
	 *
	 * <p>Unknown counts as present. A save file that is missing, unreadable, or belongs to
	 * somebody who never played here tells us nothing, and "we cannot tell" is not grounds for
	 * opening somebody's chest.
	 */
	public static boolean absentFor(MinecraftServer server, UUID player, int days) {
		if (server.getPlayerList().getPlayer(player) != null) return false;

		Path saved = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player + ".dat");
		if (!Files.isRegularFile(saved)) return false;

		try {
			long age = System.currentTimeMillis() - Files.getLastModifiedTime(saved).toMillis();
			return age >= days * 24L * 60L * 60L * 1000L;
		} catch (IOException e) {
			return false;
		}
	}
}
