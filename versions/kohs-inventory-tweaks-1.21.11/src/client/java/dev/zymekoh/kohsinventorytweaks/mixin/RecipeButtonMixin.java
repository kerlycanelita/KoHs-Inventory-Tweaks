package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {
	@Shadow private float animationTime;

	@Inject(method = "renderWidget", at = @At("HEAD"))
	private void kohsInventoryTweaks$removeRecipeBounce(
		final GuiGraphics graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callbackInfo
	) {
		if (ConfigStore.get().removeAllInventoryAnimations) {
			this.animationTime = 0.0F;
		}
	}
}
