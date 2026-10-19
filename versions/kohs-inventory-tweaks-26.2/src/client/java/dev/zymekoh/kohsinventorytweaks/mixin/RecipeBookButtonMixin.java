package dev.zymekoh.kohsinventorytweaks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.kohsinventorytweaks.inventory.RecipeBookHider;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hide recipe book: the screen is built without the recipe book button. */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookButtonMixin {
	@WrapOperation(method = "initButton", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"))
	private GuiEventListener kohsInventoryTweaks$skipBookButton(final AbstractRecipeBookScreen<?> screen, final GuiEventListener button,
		final Operation<GuiEventListener> original) {
		return RecipeBookHider.hidden() ? button : original.call(screen, button);
	}
}
