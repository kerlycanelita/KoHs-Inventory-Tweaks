package dev.zymekoh.kohsinventorytweaks.config;

import dev.zymekoh.kohsinventorytweaks.cursor.CursorTarget;
import dev.zymekoh.kohsinventorytweaks.inventory.ContainerScaleTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class InventoryTweaksConfig {
	public static final double DEFAULT_INVENTORY_GUI_SCALE = 2.00;
	public enum TextureSource {
		APPLIED,
		VANILLA
	}

	public enum ProfilePreset {
		CUSTOM,
		VANILLA,
		PVP,
		BUILDING,
		PERFORMANCE;

		public ProfilePreset next() {
			ProfilePreset[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}
	}

	public boolean centerMouseFix = true;
	public boolean superFastInventory = true;
	public boolean fastInventoryWhileMouseHeld;
	public boolean suppressInventoryKeyRepeats = true;
	public boolean immediateSlotTargeting = true;
	public boolean removeAllInventoryAnimations;
	public ProfilePreset activeProfile = ProfilePreset.CUSTOM;
	public boolean autoProfileSwitch;
	public ProfilePreset singleplayerProfile = ProfilePreset.BUILDING;
	public ProfilePreset multiplayerProfile = ProfilePreset.PVP;
	public boolean inventoryGuiScalerEnabled = true;
	public double inventoryGuiScale = DEFAULT_INVENTORY_GUI_SCALE;
	public boolean guiScalerWarningDismissed;
	public boolean affectAllContainers = true;
	public boolean chestCursorEnabled;
	public boolean shulkerCursorEnabled;
	public boolean enderChestCursorEnabled;
	public boolean barrelCursorEnabled;
	public TextureSource inventoryTextureSource = TextureSource.APPLIED;
	public int frameColor = 0xFFFFFF;
	public int frameOpacity = 255;
	public int slotColor = 0xFFFFFF;
	public int slotOpacity = 255;
	public int backgroundOpacity = 255;
	public int inventoryBackdropOpacity = 208;
	public int visiblePlayerDepthIntensity = 220;
	public boolean smartHighlighterEnabled;
	public boolean smartLowDurabilityEnabled = true;
	public int smartLowDurabilityThreshold = 15;
	public int smartLowDurabilityColor = 0xFF4D75;
	public boolean smartEnchantedEnabled = true;
	public int smartEnchantedColor = 0xB05CFF;
	public boolean smartHighlighterHotbar;
	public boolean accessibilitySlotFocusEnabled;
	public boolean accessibilitySlotFocusPulse = true;
	public int accessibilitySlotFocusOpacity = 210;
	public int accessibilitySlotFocusColor = 0xD7A3FF;
	public boolean visiblePlayerGlowEnabled;
	public boolean visiblePlayerHighlightEnabled = true;
	public boolean visiblePlayerLightGlowEnabled = true;
	public int visiblePlayerGlowIntensity = 120;
	public int visiblePlayerGlowBrightness = 96;
	public int visiblePlayerGlowDistance = 32;
	public boolean visiblePlayerGlowPulse = true;
	public int visiblePlayerGlowColor = 0xB86BFF;
	public boolean containerProfilesEnabled;
	public boolean chestContainerScaleEnabled = true;
	public double chestContainerScale = 1.0;
	public boolean shulkerContainerScaleEnabled = true;
	public double shulkerContainerScale = 1.0;
	public boolean enderChestContainerScaleEnabled = true;
	public double enderChestContainerScale = 1.0;
	public boolean barrelContainerScaleEnabled = true;
	public double barrelContainerScale = 1.0;
	public int menuParticleDensity = 100;
	public int animatedBackgroundFps = 30;
	public boolean pauseAnimatedBackgroundWhenUnfocused = true;
	public boolean reduceParticlesWhenUnfocused = true;
	public boolean automaticBackups = true;
	public int backupRetention = 5;
	public @Nullable String customBackgroundFile;
	public List<ItemHighlight> itemHighlights = new ArrayList<>();
	/** Item the player inventory landing follows, by registry id, or null. */
	public @Nullable String inventoryLandingItem;
	public @Nullable CursorPoint inventory;
	public @Nullable CursorPoint chestSingle;
	public @Nullable CursorPoint chestDouble;
	public @Nullable CursorPoint shulker;
	public @Nullable CursorPoint enderChest;
	public @Nullable CursorPoint barrel;

	public InventoryTweaksConfig copy() {
		InventoryTweaksConfig copy = new InventoryTweaksConfig();
		copy.centerMouseFix = this.centerMouseFix;
		copy.superFastInventory = this.superFastInventory;
		copy.fastInventoryWhileMouseHeld = this.fastInventoryWhileMouseHeld;
		copy.suppressInventoryKeyRepeats = this.suppressInventoryKeyRepeats;
		copy.immediateSlotTargeting = this.immediateSlotTargeting;
		copy.removeAllInventoryAnimations = this.removeAllInventoryAnimations;
		copy.activeProfile = this.activeProfile;
		copy.autoProfileSwitch = this.autoProfileSwitch;
		copy.singleplayerProfile = this.singleplayerProfile;
		copy.multiplayerProfile = this.multiplayerProfile;
		copy.inventoryGuiScalerEnabled = this.inventoryGuiScalerEnabled;
		copy.inventoryGuiScale = this.inventoryGuiScale;
		copy.guiScalerWarningDismissed = this.guiScalerWarningDismissed;
		copy.affectAllContainers = this.affectAllContainers;
		copy.chestCursorEnabled = this.chestCursorEnabled;
		copy.shulkerCursorEnabled = this.shulkerCursorEnabled;
		copy.enderChestCursorEnabled = this.enderChestCursorEnabled;
		copy.barrelCursorEnabled = this.barrelCursorEnabled;
		copy.inventoryTextureSource = this.inventoryTextureSource;
		copy.frameColor = this.frameColor;
		copy.frameOpacity = this.frameOpacity;
		copy.slotColor = this.slotColor;
		copy.slotOpacity = this.slotOpacity;
		copy.backgroundOpacity = this.backgroundOpacity;
		copy.inventoryBackdropOpacity = this.inventoryBackdropOpacity;
		copy.visiblePlayerDepthIntensity = this.visiblePlayerDepthIntensity;
		copy.smartHighlighterEnabled = this.smartHighlighterEnabled;
		copy.smartLowDurabilityEnabled = this.smartLowDurabilityEnabled;
		copy.smartLowDurabilityThreshold = this.smartLowDurabilityThreshold;
		copy.smartLowDurabilityColor = this.smartLowDurabilityColor;
		copy.smartEnchantedEnabled = this.smartEnchantedEnabled;
		copy.smartEnchantedColor = this.smartEnchantedColor;
		copy.smartHighlighterHotbar = this.smartHighlighterHotbar;
		copy.accessibilitySlotFocusEnabled = this.accessibilitySlotFocusEnabled;
		copy.accessibilitySlotFocusPulse = this.accessibilitySlotFocusPulse;
		copy.accessibilitySlotFocusOpacity = this.accessibilitySlotFocusOpacity;
		copy.accessibilitySlotFocusColor = this.accessibilitySlotFocusColor;
		copy.visiblePlayerGlowEnabled = this.visiblePlayerGlowEnabled;
		copy.visiblePlayerHighlightEnabled = this.visiblePlayerHighlightEnabled;
		copy.visiblePlayerLightGlowEnabled = this.visiblePlayerLightGlowEnabled;
		copy.visiblePlayerGlowIntensity = this.visiblePlayerGlowIntensity;
		copy.visiblePlayerGlowBrightness = this.visiblePlayerGlowBrightness;
		copy.visiblePlayerGlowDistance = this.visiblePlayerGlowDistance;
		copy.visiblePlayerGlowPulse = this.visiblePlayerGlowPulse;
		copy.visiblePlayerGlowColor = this.visiblePlayerGlowColor;
		copy.containerProfilesEnabled = this.containerProfilesEnabled;
		copy.chestContainerScaleEnabled = this.chestContainerScaleEnabled;
		copy.chestContainerScale = this.chestContainerScale;
		copy.shulkerContainerScaleEnabled = this.shulkerContainerScaleEnabled;
		copy.shulkerContainerScale = this.shulkerContainerScale;
		copy.enderChestContainerScaleEnabled = this.enderChestContainerScaleEnabled;
		copy.enderChestContainerScale = this.enderChestContainerScale;
		copy.barrelContainerScaleEnabled = this.barrelContainerScaleEnabled;
		copy.barrelContainerScale = this.barrelContainerScale;
		copy.menuParticleDensity = this.menuParticleDensity;
		copy.animatedBackgroundFps = this.animatedBackgroundFps;
		copy.pauseAnimatedBackgroundWhenUnfocused = this.pauseAnimatedBackgroundWhenUnfocused;
		copy.reduceParticlesWhenUnfocused = this.reduceParticlesWhenUnfocused;
		copy.automaticBackups = this.automaticBackups;
		copy.backupRetention = this.backupRetention;
		copy.customBackgroundFile = this.customBackgroundFile;
		copy.itemHighlights = new ArrayList<>();
		for (ItemHighlight highlight : this.itemHighlights) {
			if (highlight != null) {
				copy.itemHighlights.add(highlight.copy());
			}
		}
		copy.inventoryLandingItem = this.inventoryLandingItem;
		copy.inventory = copyPoint(this.inventory);
		copy.chestSingle = copyPoint(this.chestSingle);
		copy.chestDouble = copyPoint(this.chestDouble);
		copy.shulker = copyPoint(this.shulker);
		copy.enderChest = copyPoint(this.enderChest);
		copy.barrel = copyPoint(this.barrel);
		return copy;
	}

	public @Nullable CursorPoint getPosition(final CursorTarget target) {
		return switch (target) {
			case INVENTORY -> this.inventory;
			case CHEST_SINGLE -> this.chestSingle;
			case CHEST_DOUBLE -> this.chestDouble;
			case SHULKER -> this.shulker;
			case ENDER_CHEST -> this.enderChest;
			case BARREL -> this.barrel;
		};
	}

	public void setPosition(final CursorTarget target, final @Nullable CursorPoint point) {
		CursorPoint sanitized = copyPoint(point);
		switch (target) {
			case INVENTORY -> this.inventory = sanitized;
			case CHEST_SINGLE -> this.chestSingle = sanitized;
			case CHEST_DOUBLE -> this.chestDouble = sanitized;
			case SHULKER -> this.shulker = sanitized;
			case ENDER_CHEST -> this.enderChest = sanitized;
			case BARREL -> this.barrel = sanitized;
		}
	}

	public boolean isCursorEnabled(final CursorTarget target) {
		return switch (target) {
			case INVENTORY -> true;
			case CHEST_SINGLE, CHEST_DOUBLE -> this.chestCursorEnabled;
			case SHULKER -> this.shulkerCursorEnabled;
			case ENDER_CHEST -> this.enderChestCursorEnabled;
			case BARREL -> this.barrelCursorEnabled;
		};
	}

	public void setCursorEnabled(final CursorTarget target, final boolean enabled) {
		switch (target) {
			case INVENTORY -> {
			}
			case CHEST_SINGLE, CHEST_DOUBLE -> this.chestCursorEnabled = enabled;
			case SHULKER -> this.shulkerCursorEnabled = enabled;
			case ENDER_CHEST -> this.enderChestCursorEnabled = enabled;
			case BARREL -> this.barrelCursorEnabled = enabled;
		}
	}

	public void resetCursorPositions() {
		this.chestCursorEnabled = false;
		this.shulkerCursorEnabled = false;
		this.enderChestCursorEnabled = false;
		this.barrelCursorEnabled = false;
		this.inventoryLandingItem = null;
		this.inventory = null;
		this.chestSingle = null;
		this.chestDouble = null;
		this.shulker = null;
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
		this.visiblePlayerDepthIntensity = 220;
		this.customBackgroundFile = null;
	}

	public void resetGuiScaler() {
		this.inventoryGuiScalerEnabled = true;
		this.inventoryGuiScale = DEFAULT_INVENTORY_GUI_SCALE;
		this.affectAllContainers = true;
	}

	public boolean isContainerScaleEnabled(final ContainerScaleTarget target) {
		return switch (target) {
			case CHEST_SINGLE, CHEST_DOUBLE -> this.chestContainerScaleEnabled;
			case SHULKER -> this.shulkerContainerScaleEnabled;
			case ENDER_CHEST -> this.enderChestContainerScaleEnabled;
			case BARREL -> this.barrelContainerScaleEnabled;
		};
	}

	public double containerScale(final ContainerScaleTarget target) {
		return switch (target) {
			case CHEST_SINGLE, CHEST_DOUBLE -> this.chestContainerScale;
			case SHULKER -> this.shulkerContainerScale;
			case ENDER_CHEST -> this.enderChestContainerScale;
			case BARREL -> this.barrelContainerScale;
		};
	}

	/** Applies only behavior and performance defaults; personal art and item lists survive. */
	public void applyProfile(final ProfilePreset requested) {
		ProfilePreset preset = requested == null ? ProfilePreset.CUSTOM : requested;
		this.activeProfile = preset;
		switch (preset) {
			case CUSTOM -> {
			}
			case VANILLA -> {
				this.centerMouseFix = true;
				this.superFastInventory = false;
				this.fastInventoryWhileMouseHeld = false;
				this.suppressInventoryKeyRepeats = false;
				this.immediateSlotTargeting = false;
				this.removeAllInventoryAnimations = false;
				this.inventoryGuiScalerEnabled = false;
				this.smartHighlighterEnabled = false;
				this.accessibilitySlotFocusEnabled = false;
				this.visiblePlayerGlowEnabled = false;
				this.menuParticleDensity = 100;
				this.animatedBackgroundFps = 60;
			}
			case PVP -> {
				this.fastInventoryWhileMouseHeld = true;
				this.suppressInventoryKeyRepeats = true;
				this.immediateSlotTargeting = true;
				this.centerMouseFix = true;
				this.superFastInventory = true;
				this.removeAllInventoryAnimations = true;
				this.smartHighlighterEnabled = true;
				this.smartLowDurabilityEnabled = true;
				this.smartHighlighterHotbar = true;
				this.visiblePlayerGlowEnabled = false;
				this.menuParticleDensity = 35;
				this.animatedBackgroundFps = 20;
			}
			case BUILDING -> {
				this.fastInventoryWhileMouseHeld = false;
				this.suppressInventoryKeyRepeats = true;
				this.immediateSlotTargeting = true;
				this.centerMouseFix = true;
				this.superFastInventory = true;
				this.removeAllInventoryAnimations = false;
				this.smartHighlighterEnabled = false;
				this.accessibilitySlotFocusEnabled = true;
				this.visiblePlayerGlowEnabled = false;
				this.menuParticleDensity = 100;
				this.animatedBackgroundFps = 30;
			}
			case PERFORMANCE -> {
				this.fastInventoryWhileMouseHeld = true;
				this.suppressInventoryKeyRepeats = true;
				this.immediateSlotTargeting = true;
				this.centerMouseFix = true;
				this.superFastInventory = true;
				this.removeAllInventoryAnimations = true;
				this.smartHighlighterEnabled = false;
				this.accessibilitySlotFocusEnabled = false;
				this.visiblePlayerGlowEnabled = false;
				this.menuParticleDensity = 0;
				this.animatedBackgroundFps = 5;
			}
		}
	}

	public @Nullable ItemHighlight findItemHighlight(final String itemId) {
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
			&& this.fastInventoryWhileMouseHeld == other.fastInventoryWhileMouseHeld
			&& this.suppressInventoryKeyRepeats == other.suppressInventoryKeyRepeats
			&& this.immediateSlotTargeting == other.immediateSlotTargeting
			&& this.removeAllInventoryAnimations == other.removeAllInventoryAnimations
			&& this.activeProfile == other.activeProfile
			&& this.autoProfileSwitch == other.autoProfileSwitch
			&& this.singleplayerProfile == other.singleplayerProfile
			&& this.multiplayerProfile == other.multiplayerProfile
			&& this.inventoryGuiScalerEnabled == other.inventoryGuiScalerEnabled
			&& Double.compare(this.inventoryGuiScale, other.inventoryGuiScale) == 0
			&& this.guiScalerWarningDismissed == other.guiScalerWarningDismissed
			&& this.affectAllContainers == other.affectAllContainers
			&& this.chestCursorEnabled == other.chestCursorEnabled
			&& this.shulkerCursorEnabled == other.shulkerCursorEnabled
			&& this.enderChestCursorEnabled == other.enderChestCursorEnabled
			&& this.barrelCursorEnabled == other.barrelCursorEnabled
			&& this.inventoryTextureSource == other.inventoryTextureSource
			&& this.frameColor == other.frameColor
			&& this.frameOpacity == other.frameOpacity
			&& this.slotColor == other.slotColor
			&& this.slotOpacity == other.slotOpacity
			&& this.backgroundOpacity == other.backgroundOpacity
			&& this.inventoryBackdropOpacity == other.inventoryBackdropOpacity
			&& this.visiblePlayerDepthIntensity == other.visiblePlayerDepthIntensity
			&& this.smartHighlighterEnabled == other.smartHighlighterEnabled
			&& this.smartLowDurabilityEnabled == other.smartLowDurabilityEnabled
			&& this.smartLowDurabilityThreshold == other.smartLowDurabilityThreshold
			&& this.smartLowDurabilityColor == other.smartLowDurabilityColor
			&& this.smartEnchantedEnabled == other.smartEnchantedEnabled
			&& this.smartEnchantedColor == other.smartEnchantedColor
			&& this.smartHighlighterHotbar == other.smartHighlighterHotbar
			&& this.accessibilitySlotFocusEnabled == other.accessibilitySlotFocusEnabled
			&& this.accessibilitySlotFocusPulse == other.accessibilitySlotFocusPulse
			&& this.accessibilitySlotFocusOpacity == other.accessibilitySlotFocusOpacity
			&& this.accessibilitySlotFocusColor == other.accessibilitySlotFocusColor
			&& this.visiblePlayerGlowEnabled == other.visiblePlayerGlowEnabled
			&& this.visiblePlayerHighlightEnabled == other.visiblePlayerHighlightEnabled
			&& this.visiblePlayerLightGlowEnabled == other.visiblePlayerLightGlowEnabled
			&& this.visiblePlayerGlowIntensity == other.visiblePlayerGlowIntensity
			&& this.visiblePlayerGlowBrightness == other.visiblePlayerGlowBrightness
			&& this.visiblePlayerGlowDistance == other.visiblePlayerGlowDistance
			&& this.visiblePlayerGlowPulse == other.visiblePlayerGlowPulse
			&& this.visiblePlayerGlowColor == other.visiblePlayerGlowColor
			&& this.containerProfilesEnabled == other.containerProfilesEnabled
			&& this.chestContainerScaleEnabled == other.chestContainerScaleEnabled
			&& Double.compare(this.chestContainerScale, other.chestContainerScale) == 0
			&& this.shulkerContainerScaleEnabled == other.shulkerContainerScaleEnabled
			&& Double.compare(this.shulkerContainerScale, other.shulkerContainerScale) == 0
			&& this.enderChestContainerScaleEnabled == other.enderChestContainerScaleEnabled
			&& Double.compare(this.enderChestContainerScale, other.enderChestContainerScale) == 0
			&& this.barrelContainerScaleEnabled == other.barrelContainerScaleEnabled
			&& Double.compare(this.barrelContainerScale, other.barrelContainerScale) == 0
			&& this.menuParticleDensity == other.menuParticleDensity
			&& this.animatedBackgroundFps == other.animatedBackgroundFps
			&& this.pauseAnimatedBackgroundWhenUnfocused == other.pauseAnimatedBackgroundWhenUnfocused
			&& this.reduceParticlesWhenUnfocused == other.reduceParticlesWhenUnfocused
			&& this.automaticBackups == other.automaticBackups
			&& this.backupRetention == other.backupRetention
			&& Objects.equals(this.customBackgroundFile, other.customBackgroundFile)
			&& Objects.equals(this.itemHighlights, other.itemHighlights)
			&& Objects.equals(this.inventoryLandingItem, other.inventoryLandingItem)
			&& Objects.equals(this.inventory, other.inventory)
			&& Objects.equals(this.chestSingle, other.chestSingle)
			&& Objects.equals(this.chestDouble, other.chestDouble)
			&& Objects.equals(this.shulker, other.shulker)
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

	private static @Nullable CursorPoint copyPoint(final @Nullable CursorPoint point) {
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
