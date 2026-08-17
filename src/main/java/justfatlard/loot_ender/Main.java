package justfatlard.loot_ender;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements ModInitializer {
	public static final String MOD_ID = "loot-ender";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// The client keeps no marks across a reconnect, so a join states them all
		// rather than trusting what it still has.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			LootMarks.restate(handler.getPlayer()));

		// A broken chest takes everyone's copy with it. Without this the copies
		// would outlive the block and hand their contents back to whatever got
		// built on the spot.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				LootVault vault = LootVault.get(serverLevel);
				vault.forget(pos);
				for (ServerPlayer online : serverLevel.players()) {
					LootMarks.refresh(online, pos, false);
				}
			}
		});

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}
}
