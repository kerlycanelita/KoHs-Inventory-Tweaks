package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.NextGenStyle;
import dev.zymekoh.kohsinventorytweaks.inventory.NextGenInventory;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A slot moved outside the Vanilla panel must not read as a click outside the inventory. */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class NextGenClickOutsideMixin {
	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$movedSlotsAreInside(
		final double mouseX,
		final double mouseY,
		final int left,
		final int top,
		final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		if ((Object) this instanceof InventoryScreen screen) {
			NextGenStyle style = NextGenInventory.activeStyle(screen);
			if (style != NextGenStyle.OFF) {
				callbackInfo.setReturnValue(NextGenInventory.clickedOutside(style, mouseX, mouseY, left, top));
			}
		}
	}
}
