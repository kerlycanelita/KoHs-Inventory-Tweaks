package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.ItemHighlight;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.screen.UiRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ItemHighlighterController {
	private static String activeDynamicItem;

	private ItemHighlighterController() {
	}

	public static void drawContainerSlot(
		final GuiGraphics graphics,
		final AbstractContainerScreen<?> screen,
		final Slot slot,
		final boolean foreground
	) {
		ItemHighlight highlight = highlightFor(slot.getItem());
		if (highlight == null || highlight.dynamicHighlight && !highlight.itemId.equals(activeDynamicItem)) {
			return;
		}
		drawHighlightLayer(graphics, slot.x - 1, slot.y - 1, 18, highlight, foreground);
	}

	public static void drawHotbarSlot(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final ItemStack stack,
		final boolean foreground
	) {
		ItemHighlight highlight = highlightFor(stack);
		if (highlight == null || !highlight.highlightHotbar) {
			return;
		}
		drawHighlightLayer(graphics, x - 1, y - 1, 18, highlight, foreground);
	}

	public static void drawHighlightLayer(
		final GuiGraphics graphics,
		final int x,
		final int y,
		final int size,
		final ItemHighlight highlight,
		final boolean foreground
	) {
		if (foreground) {
			UiRender.outline(graphics, x, y, size, size, 0xFF000000 | highlight.borderColor & 0xFFFFFF);
			UiRender.outline(graphics, x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2), 0x72000000 | highlight.borderColor & 0xFFFFFF);
		} else {
			UiRender.roundedRect(graphics, x, y, size, size, 3, 0xA8000000 | highlight.backgroundColor & 0xFFFFFF);
		}
	}

	public static void updateDynamicHover(final AbstractContainerScreen<?> screen) {
		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
		Slot hovered = accessor.kohsInventoryTweaks$getHoveredSlot();
		ItemHighlight highlight = hovered == null ? null : highlightFor(hovered.getItem());
		activeDynamicItem = highlight != null && highlight.dynamicHighlight ? highlight.itemId : null;
	}

	public static void onContainerClosed() {
		activeDynamicItem = null;
	}

	public static ItemHighlight highlightFor(final ItemStack stack) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.ITEM_HIGHLIGHTER)
			|| stack == null || stack.isEmpty()) {
			return null;
		}
		return ConfigStore.get().findItemHighlight(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}
}
