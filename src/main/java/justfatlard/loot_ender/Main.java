package justfatlard.loot_ender;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import java.util.List;
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
		LootEnderConfig.load();
		if (justfatlard.pandorical.api.PandoricalApi.isAvailable()) LootEnderConfig.menu();
		LootEnderItems.register();

		// Registered whatever the config says, so that turning lockpicking on does not need a
		// restart to make the screen answer, and turning it off leaves no half-wired handler.
		justfatlard.loot_ender.lock.Lockpicking.registerHandlers();
		justfatlard.loot_ender.lock.LockpickLoot.register();

		// The clasps are this mod's own art, and this mod is not on anybody's client. Pandorical
		// carries them over on connect and reloads resources afterwards, which is what gets them
		// into the chest atlas the renderer samples.
		justfatlard.pandorical.api.PandoricalApi.content().registerModAssets(MOD_ID);

      // Guarded class load: the tip registration names block-tip types.
      if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("block-tip")) {
         justfatlard.loot_ender.LootTips.register();
      }

		// The client keeps no marks across a reconnect, so state them all - on Pandorical's
		// ready moment, not Fabric's JOIN, which fires before the capability handshake and
		// had every one of these silently dropped.
		justfatlard.pandorical.api.PandoricalApi.onPlayerReady(LootMarks::restate);

		// A lid is held open by a tally that lives in this process, so both ways of leaving
		// without shutting one have to be swept: the player who disconnects mid-screen, and
		// the process itself. A barrel is the one that cannot forgive being missed - its open
		// state is written to the world and outlives the tally that set it.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			LootLid.left(server, handler.getPlayer().getUUID()));

		ServerLifecycleEvents.SERVER_STOPPING.register(LootLid::shutDown);

		// A broken chest takes everyone's copy with it. Without this the copies
		// would outlive the block and hand their contents back to whatever got
		// built on the spot.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel serverLevel) {
				LootVault vault = LootVault.get(serverLevel);
				vault.forget(pos);
				justfatlard.loot_ender.lock.LockVault.get(serverLevel).forget(pos);
				LootIndex.forget(serverLevel, pos);
				for (ServerPlayer online : serverLevel.players()) {
					// Unmarked, not marked unopened: there is no chest here to open.
					LootMarks.gone(online, pos);
				}
			}
		});

		// A loot chest has to be recognisable before anybody opens it, and until its chunk
		// arrives nothing on the server knows it exists. This is the moment it becomes
		// knowable, so it is the moment both clasps are handed out - the dark one included,
		// because a chest this player already emptied is still a thing they need told.
		ServerChunkEvents.CHUNK_LOAD.register((serverLevel, chunk, newlyGenerated) -> {
			List<BlockPos> fresh = LootIndex.noticed(serverLevel, chunk);
			if (fresh.isEmpty()) return;

			LootVault vault = LootVault.get(serverLevel);
			for (ServerPlayer online : serverLevel.players()) {
				LootMarks.noticed(online,
					fresh.stream().filter(pos -> vault.isSpent(online.getUUID(), pos)).toList(),
					fresh.stream().filter(pos -> !vault.isSpent(online.getUUID(), pos)).toList());
			}
		});

		LOGGER.info("[{}] Loaded (server-side with Pandorical)", MOD_ID);
	}
}
