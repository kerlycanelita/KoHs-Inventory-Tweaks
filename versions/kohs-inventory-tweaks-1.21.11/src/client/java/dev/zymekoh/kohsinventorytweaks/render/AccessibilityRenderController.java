package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.screen.UiRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

public final class AccessibilityRenderController {
	private AccessibilityRenderController() {
	}

	public static void drawFocusedSlot(
		final GuiGraphics graphics,
		final AbstractContainerScreen<?> screen,
		final Slot slot
	) {
		InventoryTweaksConfig config = ConfigStore.get();
		if (!config.accessibilitySlotFocusEnabled
			|| ((AbstractContainerScreenAccessor) screen).kohsInventoryTweaks$getHoveredSlot() != slot) {
			return;
		}
		int alpha = config.accessibilitySlotFocusOpacity;
		if (config.accessibilitySlotFocusPulse && !InventoryAnimationController.suppressAllInventoryAnimations()) {
			double wave = 0.78 + 0.22 * Math.sin(System.nanoTime() / 180_000_000.0);
			alpha = (int) Math.round(alpha * wave);
		}
		int color = UiRender.withAlpha(config.accessibilitySlotFocusColor, alpha);
		graphics.renderOutline(slot.x - 2, slot.y - 2, 20, 20, color);
		graphics.renderOutline(slot.x - 1, slot.y - 1, 18, 18, UiRender.withAlpha(0xFFFFFF, Math.min(180, alpha)));
	}

	public static void drawPreviewFocus(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final InventoryTweaksConfig config
	) {
		if (config == null || !config.accessibilitySlotFocusEnabled) {
			return;
		}
		int alpha = config.accessibilitySlotFocusOpacity;
		if (config.accessibilitySlotFocusPulse && !InventoryAnimationController.suppressAllInventoryAnimations()) {
			alpha = (int) Math.round(alpha * (0.78 + 0.22 * Math.sin(System.nanoTime() / 180_000_000.0)));
		}
		graphics.renderOutline(x - 2, y - 2, 20, 20, UiRender.withAlpha(config.accessibilitySlotFocusColor, alpha));
		graphics.renderOutline(x - 1, y - 1, 18, 18, UiRender.withAlpha(0xFFFFFF, Math.min(180, alpha)));
	}
}

