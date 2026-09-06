package justfatlard.loot_ender;

import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * The mod's one item.
 *
 * <p>Registered whether or not lockpicking is switched on. A config flag is a thing an admin
 * flips on a Tuesday, and an item that stops existing when they do would turn every pick anybody
 * was carrying into an unknown item the next time their chunk loaded. What the flag turns off is
 * where picks come from and what they are for, not whether the game has heard of them.
 */
public final class LootEnderItems {
	public static final Identifier LOCKPICK_ID = Identifier.fromNamespaceAndPath(Main.MOD_ID, "lockpick");

	public static final ResourceKey<Item> LOCKPICK_KEY = ResourceKey.create(Registries.ITEM, LOCKPICK_ID);

	public static final Item LOCKPICK = new Item(
		new Item.Properties().setId(LOCKPICK_KEY)
	);

	private LootEnderItems() {}

	public static void register() {
		PandoricalApi.content().registerItem(Main.MOD_ID + ":lockpick", new ItemRegistration()
			.model(Main.MOD_ID + ":item/lockpick"));

		Registry.register(BuiltInRegistries.ITEM, LOCKPICK_ID, LOCKPICK);
	}
}
