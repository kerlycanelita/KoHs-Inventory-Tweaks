package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolves input against the latest delivered pointer, without waiting for a render. */
@Mixin(AbstractContainerScreen.class)
public abstract class InventorySlotInputMixin {
	@Inject(method = "keyPressed", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;checkHotbarKeyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
	private void kohsInventoryTweaks$refreshKeyboardTarget(final KeyEvent event, final CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		this.kohsInventoryTweaks$refreshTarget(
			minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
			minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()), true);
	}

	@Inject(method = "checkHotbarMouseClicked", at = @At("HEAD"))
	private void kohsInventoryTweaks$refreshMouseBindingTarget(final MouseButtonEvent event, final CallbackInfo ci) {
		// The inherited click path already converted this event to inventory coordinates.
		this.kohsInventoryTweaks$refreshTarget(event.x(), event.y(), false);
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"))
	private void kohsInventoryTweaks$refreshScrollTarget(final double x, final double y,
		final double horizontal, final double vertical, final CallbackInfoReturnable<Boolean> cir) {
		this.kohsInventoryTweaks$refreshTarget(x, y, true);
	}

	@Unique
	private void kohsInventoryTweaks$refreshTarget(final double x, final double y, final boolean screenCoordinates) {
		Screen screen = (Screen) (Object) this;
		Minecraft minecraft = Minecraft.getInstance();
		var config = ConfigStore.get();
		if (screen.getClass() != InventoryScreen.class || minecraft.screen != screen
			|| minecraft.getOverlay() != null || !minecraft.isWindowActive()
			|| !config.immediateSlotTargeting
			|| !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) return;

		double scale = screenCoordinates ? InventoryGuiScaler.appliedSurfaceScale(screen, config) : 1.0;
		var access = (AbstractContainerScreenAccessor) this;
		Slot previous = access.kohsInventoryTweaks$getHoveredSlot();
		Slot current = access.kohsInventoryTweaks$findHoveredSlot(
			InventoryGuiScaler.toInventoryCoordinate(x, screen.width, scale),
			InventoryGuiScaler.toInventoryCoordinate(y, screen.height, scale));
		if (previous != current) {
			access.kohsInventoryTweaks$setHoveredSlot(current);
			// Preserve Vanilla's bundle/slot cleanup when leaving the previous target.
			if (previous != null) access.kohsInventoryTweaks$stopHovering(previous);
		}
	}
}
