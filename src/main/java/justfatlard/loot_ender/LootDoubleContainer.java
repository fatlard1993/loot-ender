package justfatlard.loot_ender;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;

/**
 * The two halves of a double chest, joined the way vanilla joins them.
 *
 * <p>Exists only to be recognisable. A plain CompoundContainer is what the slots
 * of a double chest report as their container, so without a type of our own the
 * take-only rule would look right, pass every test on a single chest, and let
 * players stash things in every double chest in the world.
 */
public class LootDoubleContainer extends CompoundContainer implements TakeOnly {
	public LootDoubleContainer(Container first, Container second) {
		super(first, second);
	}
}
