package justfatlard.loot_ender;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Which loot chest this is, for filing everybody's copy of it: an identity carried on the chest
 * itself, not the place it stands.
 *
 * <p>Filed by position, a chest that moves is a new chest. Big Boats carries a ship's blocks whole,
 * block entity data and all, so a pirate ship's hold sailed to a new mooring held a fresh roll for
 * every player each time it docked. A chest minecart is filed by its own identity for the same
 * reason; this is that, for a block.
 *
 * <p>Handed out the first time anybody opens the chest, and saved on its block entity, so it
 * travels wherever the block's data goes. A chest that somebody had already opened under the old
 * filing keeps the position it was opened at as its identity, so nothing anyone took before is
 * forgotten; a chest nobody has opened gets a fresh one, packed at a height no block can stand at,
 * so it can never be mistaken for a position.
 */
public final class LootKey {
	private LootKey() {}

	public static final AttachmentType<Long> KEY = AttachmentRegistry.<Long>builder()
		.persistent(Codec.LONG)
		.buildAndRegister(Identifier.fromNamespaceAndPath(Main.MOD_ID, "chest_key"));

	/**
	 * Registers the attachment. Called at start-up: an attachment nobody has registered when a
	 * chunk loads is thrown away with a warning, and every chest in that chunk would be handed a
	 * new identity, and a new roll, the next time it was opened.
	 */
	public static void register() {}

	/** Above the build limit of any dimension, so an identity never reads as a block that exists. */
	private static final int IDENTITY_Y = 2047;

	/** The chest's identity, or where it stands for one nobody has opened since this mod filed by identity. */
	public static long of(ServerLevel level, BlockPos pos) {
		return of(level.getBlockEntity(pos), pos);
	}

	public static long of(@Nullable BlockEntity chest, BlockPos pos) {
		Long key = chest == null ? null : chest.getAttached(KEY);
		return key != null ? key : pos.asLong();
	}

	/** The chest's identity, handing it one if it has none yet. */
	public static long claim(ServerLevel level, BlockPos pos, LootVault vault) {
		BlockEntity chest = level.getBlockEntity(pos);
		if (chest == null) return pos.asLong();
		Long key = chest.getAttached(KEY);
		if (key != null) return key;

		long claimed = vault.knows(pos.asLong()) ? pos.asLong() : fresh(level.getRandom());
		chest.setAttached(KEY, claimed);
		chest.setChanged();
		LootIndex.claimed(level, pos, claimed);
		return claimed;
	}

	/** Whether a key is an identity rather than a position, and so names no place to put a mark. */
	public static boolean isIdentity(long key) {
		return BlockPos.getY(key) == IDENTITY_Y;
	}

	private static long fresh(RandomSource random) {
		return BlockPos.asLong(random.nextInt(1 << 25) - (1 << 24), IDENTITY_Y, random.nextInt(1 << 25) - (1 << 24));
	}
}
