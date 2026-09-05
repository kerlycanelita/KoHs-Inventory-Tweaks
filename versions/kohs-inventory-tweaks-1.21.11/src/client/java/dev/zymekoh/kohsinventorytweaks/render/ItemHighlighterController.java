package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.ItemHighlight;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.screen.UiRender;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ItemHighlighterController {
	private static final int HOTBAR_SLOTS = 9;
	private static int hotbarSlot;
	private static @Nullable String activeDynamicItem;
	// Resolved once per published configuration: the lookup runs for every slot of
	// every rendered frame, including the HUD hotbar during normal gameplay.
	private static @Nullable InventoryTweaksConfig indexedConfig;
	private static Map<Item, ItemHighlight> highlightIndex = Map.of();

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

	/**
	 * Resets the hotbar slot cursor at the start of a HUD hotbar pass.
	 *
	 * <p>The Gui draws the nine hotbar slots in inventory order and then, at most
	 * once, the offhand, so counting the calls identifies the slot being drawn.</p>
	 */
	public static void beginHotbar() {
		hotbarSlot = 0;
	}

	public static void drawHotbarSlot(
		final GuiGraphics graphics,
		final @Nullable Player player,
		final int x,
		final int y,
		final ItemStack drawn,
		final boolean foreground
	) {
		// The stack handed to the slot renderer is what is on screen, which is not
		// necessarily what is in the slot: a mod may substitute it for a render-only
		// preview of a pending selection. The highlight exists to find the item the
		// player actually carries, so it follows the inventory rather than the frame.
		ItemHighlight highlight = highlightFor(carriedIn(player, hotbarSlot, drawn));
		if (foreground) {
			hotbarSlot++;
		}
		if (highlight == null || !highlight.highlightHotbar) {
			return;
		}
		drawHighlightLayer(graphics, x - 1, y - 1, 18, highlight, foreground);
	}

	private static ItemStack carriedIn(
		final @Nullable Player player,
		final int slot,
		final ItemStack drawn
	) {
		if (player == null) {
			return drawn;
		}
		if (slot >= 0 && slot < HOTBAR_SLOTS) {
			return player.getInventory().getItem(slot);
		}
		if (slot == HOTBAR_SLOTS) {
			return player.getOffhandItem();
		}
		return drawn;
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
			graphics.renderOutline(x, y, size, size, 0xFF000000 | highlight.borderColor & 0xFFFFFF);
			graphics.renderOutline(x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2), 0x72000000 | highlight.borderColor & 0xFFFFFF);
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

	public static @Nullable ItemHighlight highlightFor(final ItemStack stack) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.ITEM_HIGHLIGHTER)
			|| stack == null || stack.isEmpty()) {
			return null;
		}
		return highlightFor(ConfigStore.get(), stack);
	}

	public static @Nullable ItemHighlight highlightFor(
		final InventoryTweaksConfig config,
		final ItemStack stack
	) {
		if (config == null || stack == null || stack.isEmpty()) {
			return null;
		}
		Map<Item, ItemHighlight> index = index(config);
		return index.get(stack.getItem());
	}

	private static Map<Item, ItemHighlight> index(final InventoryTweaksConfig config) {
		if (config == indexedConfig) {
			return highlightIndex;
		}
		Map<Item, ItemHighlight> resolved = new HashMap<>();
		if (config != null && config.itemHighlights != null) {
			for (ItemHighlight highlight : config.itemHighlights) {
				if (highlight == null) {
					continue;
				}
				Identifier itemId = Identifier.tryParse(highlight.itemId);
				Item item = itemId == null ? null : BuiltInRegistries.ITEM.getValue(itemId);
				if (item != null) {
					resolved.putIfAbsent(item, highlight);
				}
			}
		}
		highlightIndex = resolved.isEmpty() ? Map.of() : resolved;
		indexedConfig = config;
		return highlightIndex;
	}
}
