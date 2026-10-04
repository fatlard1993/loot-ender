package justfatlard.loot_ender.gametest;

import justfatlard.loot_ender.LootKey;
import justfatlard.loot_ender.LootVault;
import justfatlard.loot_ender.PlayerLootContainer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * A loot chest that moves is the same chest. Big Boats carries a ship's blocks to wherever it docks,
 * block entity data and all, which is how a pirate ship's hold came back full at every mooring.
 * This moves a chest the way Big Boats does - its data saved, the block taken away, put back down
 * somewhere else from that data - and asks whether a player who emptied it gets a second helping.
 */
public final class LootTravels implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		// Before anything here touches the key: a chunk loaded ahead of its registration has every
		// chest's identity thrown away on load. Asked first in the first test, so nothing has yet.
		check(net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl.get(
				net.minecraft.resources.Identifier.fromNamespaceAndPath("loot-ender", "chest_key")) != null,
			"a chest's identity is registered at start-up, before any chunk loads");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = world.getConnection().getServerPlayer();
				BlockPos here = player.blockPosition().offset(2, 0, 0);
				BlockPos there = player.blockPosition().offset(-2, 0, 3);
				BlockPos untouched = player.blockPosition().offset(2, 0, 3);
				BlockPos landing = player.blockPosition().offset(-2, 0, -3);

				placeLoot(level, here, 1);
				placeLoot(level, untouched, 2);
				LootVault vault = LootVault.get(level);

				PlayerLootContainer copy = open(level, player, here);
				check(!copy.isEmpty(), "the chest had loot to take: the scene is wrong");
				copy.clearContent();
				copy.stopOpen(player);

				move(level, here, there);
				check(open(level, player, there).isEmpty(), "an emptied chest carried somewhere else comes back empty");
				check(vault.isSpent(level, player.getUUID(), there), "and is marked spent where it now stands");
				check(LootKey.isIdentity(LootKey.of(level, there)), "filed under an identity of its own, not a place");

				move(level, untouched, landing);
				check(!open(level, player, landing).isEmpty(), "a chest nobody opened still has its loot after a move");
			});
			context.waitTicks(5);
		}
	}

	private static void placeLoot(ServerLevel level, BlockPos at, long seed) {
		level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
		((ChestBlockEntity) level.getBlockEntity(at)).setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, seed);
	}

	private static PlayerLootContainer open(ServerLevel level, ServerPlayer player, BlockPos at) {
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(at);
		return LootVault.get(level).copyFor(level, player, at, chest.getLootTable(), chest.getLootTableSeed());
	}

	/** As Big Boats sails a block away and docks it: saved whole, removed, put back from what was saved. */
	private static void move(ServerLevel level, BlockPos from, BlockPos to) {
		BlockState state = level.getBlockState(from);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		level.getBlockEntity(from).saveWithId(output);
		CompoundTag saved = output.buildResult();
		level.removeBlockEntity(from);
		level.setBlockAndUpdate(from, Blocks.AIR.defaultBlockState());

		level.setBlockAndUpdate(to, state);
		BlockEntity restored = BlockEntity.loadStatic(to, state, saved, level.registryAccess());
		check(restored != null, "the chest could not be put back down from its data");
		level.setBlockEntity(restored);
	}

	private static void check(boolean ok, String complaint) {
		if (!ok) throw new AssertionError(complaint);
	}
}
