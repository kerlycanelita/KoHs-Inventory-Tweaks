package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double kohsInventoryTweaks$transformInventoryMouseX(final double mouseX) {
		if (!((Object) this instanceof InventoryScreen screen)) {
			return mouseX;
		}
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryCoordinate(mouseX, screen.width, scale);
	}

	@ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double kohsInventoryTweaks$transformInventoryMouseY(final double mouseY) {
		if (!((Object) this instanceof InventoryScreen screen)) {
			return mouseY;
		}
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryCoordinate(mouseY, screen.height, scale);
	}
}
