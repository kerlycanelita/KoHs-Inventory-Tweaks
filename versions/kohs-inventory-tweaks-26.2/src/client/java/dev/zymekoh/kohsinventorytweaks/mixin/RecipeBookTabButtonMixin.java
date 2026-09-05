package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.render.InventoryAnimationController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookTabButton.class)
public abstract class RecipeBookTabButtonMixin {
	@Shadow private float animationTime;

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void kohsInventoryTweaks$removeTabBounce(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float partialTick,
		final CallbackInfo callbackInfo
	) {
		if (InventoryAnimationController.suppressAllInventoryAnimations()) {
			this.animationTime = 0.0F;
		}
	}
}
