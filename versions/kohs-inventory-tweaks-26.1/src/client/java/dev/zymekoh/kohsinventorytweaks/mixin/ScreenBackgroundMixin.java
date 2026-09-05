package dev.zymekoh.kohsinventorytweaks.mixin;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.render.VisiblePlayerBackdropController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	private static final int VANILLA_BOTTOM_ALPHA = 208;
	private static final int VANILLA_TOP_ALPHA = 192;

	// Guarantees that a foreign mod cancelling a scaled extraction pass cannot leak
	// scale depth into the next frame's tooltip anchoring.
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
	private void kohsInventoryTweaks$resetScaledSurfaceDepth(
		final GuiGraphicsExtractor graphics,
		final int mouseX,
		final int mouseY,
		final float a,
		final CallbackInfo callbackInfo
	) {
		InventoryGuiScaler.resetScaledSurface();
	}

	@Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
	private void kohsInventoryTweaks$customizeInventoryBackdrop(
		final GuiGraphicsExtractor graphics,
		final CallbackInfo callbackInfo
	) {
		if (!((Object) this instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CUSTOMIZATION)) {
			return;
		}
		int opacity = Math.max(0, Math.min(255, ConfigStore.get().inventoryBackdropOpacity));
		int topAlpha = Math.min(255, opacity * VANILLA_TOP_ALPHA / VANILLA_BOTTOM_ALPHA);
		if (screen instanceof InventoryScreen inventoryScreen) {
			VisiblePlayerBackdropController.draw(graphics, inventoryScreen, ConfigStore.get(), topAlpha, opacity);
		} else {
			graphics.fillGradient(
				0,
				0,
				screen.width,
				screen.height,
				topAlpha << 24 | 0x101010,
				opacity << 24 | 0x101010
			);
		}
		callbackInfo.cancel();
	}
}
