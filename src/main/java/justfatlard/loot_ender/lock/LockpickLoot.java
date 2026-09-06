package justfatlard.loot_ender.lock;

import java.util.Set;

import justfatlard.loot_ender.LootEnderConfig;
import justfatlard.loot_ender.LootEnderItems;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithEnchantedBonusCondition;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * Where picks come from.
 *
 * <p>Three sources, because one is a bottleneck and a bottleneck on the only key in the game is
 * a wall. An iron nugget crafts one, so nobody is ever truly stuck; the dead carry them, so
 * clearing a room pays toward opening what is in it; and the easy chests hold them, so the first
 * lock you meet is usually not the first pick you own.
 *
 * <p>The chest tables here are deliberately the ones that come out {@link LockTier#UNLOCKED} most
 * of the time. Putting the key inside the locked box is the oldest mistake in the genre.
 */
public final class LockpickLoot {
	private LockpickLoot() {}

	/** Ordinary, early, mostly-unlocked places. */
	private static final Set<ResourceKey<LootTable>> CHESTS = Set.of(
		BuiltInLootTables.VILLAGE_TOOLSMITH,
		BuiltInLootTables.VILLAGE_WEAPONSMITH,
		BuiltInLootTables.VILLAGE_ARMORER,
		BuiltInLootTables.VILLAGE_PLAINS_HOUSE,
		BuiltInLootTables.VILLAGE_TAIGA_HOUSE,
		BuiltInLootTables.SHIPWRECK_SUPPLY,
		BuiltInLootTables.SPAWN_BONUS_CHEST);

	private static final ResourceKey<LootTable> ZOMBIE = entity("zombie");
	private static final ResourceKey<LootTable> SKELETON = entity("skeleton");
	private static final ResourceKey<LootTable> HUSK = entity("husk");
	private static final ResourceKey<LootTable> STRAY = entity("stray");

	private static final Set<ResourceKey<LootTable>> MOBS = Set.of(ZOMBIE, SKELETON, HUSK, STRAY);

	/** Empty weight against one pick: found often enough to notice, rarely enough to keep. */
	private static final int EMPTY_WEIGHT = 6;

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			// Only vanilla's own copy. A datapack that has rewritten one of these has said what
			// it wants in it, and appending to that overrules an author who was more specific.
			if (source != LootTableSource.VANILLA) return;
			// Read at table load rather than captured at startup, so turning lockpicking off and
			// reloading takes the picks back out of the world's loot.
			if (!LootEnderConfig.lockpicking()) return;

			if (CHESTS.contains(key)) {
				builder.pool(LootPool.lootPool()
					.setRolls(ContextIntProviders.exactly(1))
					.add(EmptyLootItem.emptyItem().setWeight(EMPTY_WEIGHT))
					.add(LootItem.lootTableItem(LootEnderItems.LOCKPICK).setWeight(1))
					.apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 3)))
					.build());
				return;
			}

			if (MOBS.contains(key)) {
				float chance = LootEnderConfig.lockpickDropChance();
				if (chance <= 0F) return;

				builder.pool(LootPool.lootPool()
					.setRolls(ContextIntProviders.exactly(1))
					.add(LootItem.lootTableItem(LootEnderItems.LOCKPICK))
					// Killed by a player, and boosted by looting: a rare drop in every sense
					// vanilla already means by that, so it behaves the way players expect.
					.when(LootItemKilledByPlayerCondition.killedByPlayer())
					.when(LootItemRandomChanceWithEnchantedBonusCondition.randomChanceAndLootingBoost(
						registries.lookupOrThrow(Registries.ENCHANTMENT), chance, chance / 2F))
					.build());
			}
		});
	}

	private static ResourceKey<LootTable> entity(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE,
			net.minecraft.resources.Identifier.withDefaultNamespace("entities/" + name));
	}
}
