package dev.zymekoh.kohsinventorytweaks.config;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InventoryTweaksConfig {
	public enum TextureSource {
		APPLIED,
		VANILLA
	}

	public boolean centerMouseFix = true;
	public boolean superFastInventory = true;
	public boolean removeAllInventoryAnimations;
	public boolean inventoryGuiScalerEnabled;
	public double inventoryGuiScale = 1.0;
	public boolean guiScalerWarningDismissed;
	public boolean chestCursorEnabled;
	public boolean enderChestCursorEnabled;
	public boolean barrelCursorEnabled;
	public TextureSource inventoryTextureSource = TextureSource.APPLIED;
	public int frameColor = 0xFFFFFF;
	public int frameOpacity = 255;
	public int slotColor = 0xFFFFFF;
	public int slotOpacity = 255;
	public int backgroundOpacity = 255;
	public int inventoryBackdropOpacity = 208;
	public String customBackgroundFile;
	public List<ItemHighlight> itemHighlights = new ArrayList<>();
	public CursorPoint inventory;
	public CursorPoint chestSingle;
	public CursorPoint chestDouble;
	public CursorPoint enderChest;
	public CursorPoint barrel;

	public InventoryTweaksConfig copy() {
		InventoryTweaksConfig copy = new InventoryTweaksConfig();
		copy.centerMouseFix = this.centerMouseFix;
		copy.superFastInventory = this.superFastInventory;
		copy.removeAllInventoryAnimations = this.removeAllInventoryAnimations;
		copy.inventoryGuiScalerEnabled = this.inventoryGuiScalerEnabled;
		copy.inventoryGuiScale = this.inventoryGuiScale;
		copy.guiScalerWarningDismissed = this.guiScalerWarningDismissed;
		copy.chestCursorEnabled = this.chestCursorEnabled;
		copy.enderChestCursorEnabled = this.enderChestCursorEnabled;
		copy.barrelCursorEnabled = this.barrelCursorEnabled;
		copy.inventoryTextureSource = this.inventoryTextureSource;
		copy.frameColor = this.frameColor;
		copy.frameOpacity = this.frameOpacity;
		copy.slotColor = this.slotColor;
		copy.slotOpacity = this.slotOpacity;
		copy.backgroundOpacity = this.backgroundOpacity;
		copy.inventoryBackdropOpacity = this.inventoryBackdropOpacity;
		copy.customBackgroundFile = this.customBackgroundFile;
		copy.itemHighlights = new ArrayList<>();
		for (ItemHighlight highlight : this.itemHighlights) {
			if (highlight != null) {
				copy.itemHighlights.add(highlight.copy());
			}
		}
		copy.inventory = copyPoint(this.inventory);
		copy.chestSingle = copyPoint(this.chestSingle);
		copy.chestDouble = copyPoint(this.chestDouble);
		copy.enderChest = copyPoint(this.enderChest);
		copy.barrel = copyPoint(this.barrel);
		return copy;
	}

	public CursorPoint getPosition(final CursorTarget target) {
		return switch (target) {
			case INVENTORY -> this.inventory;
			case CHEST_SINGLE -> this.chestSingle;
			case CHEST_DOUBLE -> this.chestDouble;
			case ENDER_CHEST -> this.enderChest;
			case BARREL -> this.barrel;
		};
	}

	public void setPosition(final CursorTarget target, final CursorPoint point) {
		CursorPoint sanitized = copyPoint(point);
		switch (target) {
			case INVENTORY -> this.inventory = sanitized;
			case CHEST_SINGLE -> this.chestSingle = sanitized;
			case CHEST_DOUBLE -> this.chestDouble = sanitized;
			case ENDER_CHEST -> this.enderChest = sanitized;
			case BARREL -> this.barrel = sanitized;
		}
	}

	public boolean isCursorEnabled(final CursorTarget target) {
		return switch (target) {
			case INVENTORY -> true;
			case CHEST_SINGLE, CHEST_DOUBLE -> this.chestCursorEnabled;
			case ENDER_CHEST -> this.enderChestCursorEnabled;
			case BARREL -> this.barrelCursorEnabled;
		};
	}

	public void setCursorEnabled(final CursorTarget target, final boolean enabled) {
		switch (target) {
			case INVENTORY -> {
			}
			case CHEST_SINGLE, CHEST_DOUBLE -> this.chestCursorEnabled = enabled;
			case ENDER_CHEST -> this.enderChestCursorEnabled = enabled;
			case BARREL -> this.barrelCursorEnabled = enabled;
		}
	}

	public void resetCursorPositions() {
		this.chestCursorEnabled = false;
		this.enderChestCursorEnabled = false;
		this.barrelCursorEnabled = false;
		this.inventory = null;
		this.chestSingle = null;
		this.chestDouble = null;
		this.enderChest = null;
		this.barrel = null;
	}

	public void resetCustomization() {
		this.inventoryTextureSource = TextureSource.APPLIED;
		this.frameColor = 0xFFFFFF;
		this.frameOpacity = 255;
		this.slotColor = 0xFFFFFF;
		this.slotOpacity = 255;
		this.backgroundOpacity = 255;
		this.inventoryBackdropOpacity = 208;
		this.customBackgroundFile = null;
	}

	public void resetGuiScaler() {
		this.inventoryGuiScalerEnabled = false;
		this.inventoryGuiScale = 1.0;
	}

	public ItemHighlight findItemHighlight(final String itemId) {
		if (itemId == null || this.itemHighlights == null) {
			return null;
		}
		for (ItemHighlight highlight : this.itemHighlights) {
			if (highlight != null && itemId.equals(highlight.itemId)) {
				return highlight;
			}
		}
		return null;
	}

	public ItemHighlight getOrCreateItemHighlight(final String itemId) {
		ItemHighlight existing = this.findItemHighlight(itemId);
		if (existing != null) {
			return existing;
		}
		ItemHighlight created = new ItemHighlight(itemId);
		this.itemHighlights.add(created);
		return created;
	}

	public void removeItemHighlight(final String itemId) {
		if (this.itemHighlights != null) {
			this.itemHighlights.removeIf(highlight -> highlight == null || Objects.equals(highlight.itemId, itemId));
		}
	}

	public boolean sameValues(final InventoryTweaksConfig other) {
		return other != null
			&& this.centerMouseFix == other.centerMouseFix
			&& this.superFastInventory == other.superFastInventory
			&& this.removeAllInventoryAnimations == other.removeAllInventoryAnimations
			&& this.inventoryGuiScalerEnabled == other.inventoryGuiScalerEnabled
			&& Double.compare(this.inventoryGuiScale, other.inventoryGuiScale) == 0
			&& this.guiScalerWarningDismissed == other.guiScalerWarningDismissed
			&& this.chestCursorEnabled == other.chestCursorEnabled
			&& this.enderChestCursorEnabled == other.enderChestCursorEnabled
			&& this.barrelCursorEnabled == other.barrelCursorEnabled
			&& this.inventoryTextureSource == other.inventoryTextureSource
			&& this.frameColor == other.frameColor
			&& this.frameOpacity == other.frameOpacity
			&& this.slotColor == other.slotColor
			&& this.slotOpacity == other.slotOpacity
			&& this.backgroundOpacity == other.backgroundOpacity
			&& this.inventoryBackdropOpacity == other.inventoryBackdropOpacity
			&& Objects.equals(this.customBackgroundFile, other.customBackgroundFile)
			&& Objects.equals(this.itemHighlights, other.itemHighlights)
			&& Objects.equals(this.inventory, other.inventory)
			&& Objects.equals(this.chestSingle, other.chestSingle)
			&& Objects.equals(this.chestDouble, other.chestDouble)
			&& Objects.equals(this.enderChest, other.enderChest)
			&& Objects.equals(this.barrel, other.barrel);
	}

	public static final class ItemHighlight {
		public String itemId = "";
		public int backgroundColor = 0x6B2FA0;
		public int borderColor = 0xD7A3FF;
		public boolean highlightHotbar;
		public boolean dynamicHighlight;

		public ItemHighlight() {
		}

		public ItemHighlight(final String itemId) {
			this.itemId = itemId == null ? "" : itemId;
		}

		public ItemHighlight copy() {
			ItemHighlight copy = new ItemHighlight(this.itemId);
			copy.backgroundColor = this.backgroundColor;
			copy.borderColor = this.borderColor;
			copy.highlightHotbar = this.highlightHotbar;
			copy.dynamicHighlight = this.dynamicHighlight;
			return copy;
		}

		public void reset() {
			this.backgroundColor = 0x6B2FA0;
			this.borderColor = 0xD7A3FF;
			this.highlightHotbar = false;
			this.dynamicHighlight = false;
		}

		@Override
		public boolean equals(final Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ItemHighlight highlight)) {
				return false;
			}
			return this.backgroundColor == highlight.backgroundColor
				&& this.borderColor == highlight.borderColor
				&& this.highlightHotbar == highlight.highlightHotbar
				&& this.dynamicHighlight == highlight.dynamicHighlight
				&& Objects.equals(this.itemId, highlight.itemId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				this.itemId,
				this.backgroundColor,
				this.borderColor,
				this.highlightHotbar,
				this.dynamicHighlight
			);
		}
	}

	private static CursorPoint copyPoint(final CursorPoint point) {
		return point == null ? null : new CursorPoint(point.x(), point.y());
	}

	public record CursorPoint(double x, double y) {
		public CursorPoint {
			x = clamp(x);
			y = clamp(y);
		}

		private static double clamp(final double value) {
			if (!Double.isFinite(value)) {
				return 0.5;
			}
			return Math.max(0.0, Math.min(1.0, value));
		}
	}
}
