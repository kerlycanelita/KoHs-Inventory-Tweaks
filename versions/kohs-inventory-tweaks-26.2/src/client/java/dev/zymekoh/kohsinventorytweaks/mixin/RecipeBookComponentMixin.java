package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
	@Shadow private float time;

	@Inject(method = "tick", at = @At("RETURN"))
	private void kohsInventoryTweaks$freezeRecipeSelection(
		final CallbackInfo callbackInfo
	) {
		if (ConfigStore.get().removeAllInventoryAnimations) {
			this.time = 0.0F;
		}
	}
}
