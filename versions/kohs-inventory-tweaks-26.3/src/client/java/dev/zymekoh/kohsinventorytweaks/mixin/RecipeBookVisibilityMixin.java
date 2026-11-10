package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.inventory.RecipeBookHider;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hide recipe book: the book reads as shut wherever it asks the player's book data, so it opens
 * shut and never reopens itself on a tick. The data itself is not touched, so nothing is sent.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookVisibilityMixin {
	@Inject(method = "isVisibleAccordingToBookData", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$keepShut(final CallbackInfoReturnable<Boolean> callbackInfo) {
		if (RecipeBookHider.hidden()) {
			callbackInfo.setReturnValue(false);
		}
	}
}
