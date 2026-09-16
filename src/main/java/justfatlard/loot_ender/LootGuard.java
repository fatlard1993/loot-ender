package justfatlard.loot_ender;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Loot chests stand until an op takes one down.
 *
 * <p>Each player's loot is a copy kept against the chest, so breaking the chest was the one way to
 * reach the real contents: the table rolled once, onto the ground, for whoever swung first, and
 * everyone else's copy went with the block. Blasts are refused too, since a creeper or a TNT trap
 * is the same break at arm's length. A chest minecart carrying loot is the same chest on wheels.
 */
public final class LootGuard {
	private LootGuard() {}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register(LootGuard::mayBreak);
	}

	private static boolean mayBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return true;
		if (isOp(serverPlayer) || !LootOpening.isLootContainer(serverLevel, pos)) return true;

		serverPlayer.sendSystemMessage(Component.translatable("loot-ender.unbreakable"), true);
		return false;
	}

	/** Whether a blast leaves this block standing. */
	public static boolean spares(ServerLevel level, BlockPos pos) {
		return LootOpening.isLootContainer(level, pos);
	}

	/** Whether this hit on a vehicle is refused: loot on board, and not an op's hand. */
	public static boolean spares(Entity vehicle, DamageSource source) {
		if (!(vehicle instanceof ContainerEntity container) || container.getContainerLootTable() == null) return false;
		return !(source.getEntity() instanceof ServerPlayer player && isOp(player));
	}

	private static boolean isOp(ServerPlayer player) {
		return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}
}
