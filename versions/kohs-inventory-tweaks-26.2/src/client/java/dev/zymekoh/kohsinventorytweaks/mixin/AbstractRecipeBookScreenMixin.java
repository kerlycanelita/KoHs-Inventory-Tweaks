package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Recipe book lock: a closed book loses its button, so a missed click beside the
 * player model cannot open it and slide every slot sideways in the middle of a
 * refill. An open book keeps the button, or there would be no way to close it.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@Shadow
	@Final
	private RecipeBookComponent<?> recipeBookComponent;

	@WrapOperation(method = "initButton", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
	private GuiEventListener kohsInventoryTweaks$lockClosedRecipeBook(
		final AbstractRecipeBookScreen<?> screen,
		final GuiEventListener button,
		final Operation<GuiEventListener> original
	) {
		if (ConfigStore.get().recipeBookLock && !this.recipeBookComponent.isVisible()
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return button;
		}
		return original.call(screen, button);
	}
}
