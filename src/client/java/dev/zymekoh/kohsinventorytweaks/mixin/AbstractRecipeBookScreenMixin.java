package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@ModifyVariable(method = {"mouseClicked", "mouseDragged"}, at = @At("HEAD"), argsOnly = true)
	private MouseButtonEvent kohsInventoryTweaks$transformInventoryMouse(final MouseButtonEvent event) {
		if (!((Object) this instanceof InventoryScreen screen)) {
			return event;
		}
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return InventoryGuiScaler.toInventoryEvent(event, screen.width, screen.height, scale);
	}

	@ModifyVariable(method = "mouseDragged", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double kohsInventoryTweaks$scaleInventoryDragX(final double dx) {
		return this.kohsInventoryTweaks$scaleDragDelta(dx);
	}

	@ModifyVariable(method = "mouseDragged", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double kohsInventoryTweaks$scaleInventoryDragY(final double dy) {
		return this.kohsInventoryTweaks$scaleDragDelta(dy);
	}

	private double kohsInventoryTweaks$scaleDragDelta(final double delta) {
		if (!((Object) this instanceof InventoryScreen screen)) {
			return delta;
		}
		double scale = InventoryGuiScaler.appliedScale(screen, ConfigStore.get());
		return delta / scale;
	}
}
