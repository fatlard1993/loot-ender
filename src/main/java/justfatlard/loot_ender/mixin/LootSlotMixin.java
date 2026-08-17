package justfatlard.loot_ender.mixin;

import justfatlard.loot_ender.TakeOnly;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a loot copy take-only.
 *
 * <p>A copy that accepts items is a private chest at every dungeon in the world,
 * which is both a storage trick nobody asked for and a promise to remember an
 * inventory per player per chest forever. Taking is the only thing these are
 * for, so putting is refused.
 *
 * <p>Enforced here rather than through {@code Container.canPlaceItem}, which
 * looks like the obvious place and is not: {@code Slot.mayPlace} returns a flat
 * true without ever consulting it, so overriding it on the container would have
 * changed nothing while looking exactly like a fix. {@code canPlaceItem} governs
 * hoppers, not hands.
 *
 * <p>{@code mayPlace} is the real gate. Every way an item can enter a slot goes
 * through it: the five checks in {@code doClick} covering clicks, swaps and
 * drags, and the one in {@code moveItemStackTo} covering shift-click.
 *
 * <p>Keyed on {@link TakeOnly} rather than on the copy's own class, because a
 * double chest's slots report the joined pair as their container, not either
 * half. Naming the half would have passed every single-chest test and left every
 * double chest in the world open for storage.
 */
@Mixin(Slot.class)
public class LootSlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true, require = 1)
	private void lootender$takeOnly(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (this.container instanceof TakeOnly) cir.setReturnValue(false);
	}
}
