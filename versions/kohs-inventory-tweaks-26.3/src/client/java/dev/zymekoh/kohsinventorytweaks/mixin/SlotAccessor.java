package dev.zymekoh.kohsinventorytweaks.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a slot sits on its screen, which Vanilla fixes when the menu is built. */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void kohsInventoryTweaks$setX(int x);

	@Mutable
	@Accessor("y")
	void kohsInventoryTweaks$setY(int y);
}
