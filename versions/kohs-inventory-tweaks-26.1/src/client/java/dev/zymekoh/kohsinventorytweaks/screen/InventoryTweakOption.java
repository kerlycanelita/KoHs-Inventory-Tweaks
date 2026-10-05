package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;

/** Shared option metadata for rendering, narration, state changes and warnings. */
enum InventoryTweakOption {
	FAST("screen.kohs_inventory_tweaks.super_fast_inventory"),
	CENTER("screen.kohs_inventory_tweaks.center_mouse_fix"),
	POINTER("screen.kohs_inventory_tweaks.shortcuts_follow_pointer"),
	PIXEL("screen.kohs_inventory_tweaks.pixel_perfect_scale"),
	STEADY("screen.kohs_inventory_tweaks.steady_tooltips"),
	RECIPE("screen.kohs_inventory_tweaks.recipe_book_lock"),
	TOTEM("screen.kohs_inventory_tweaks.totem_guard"),
	SWAP("screen.kohs_inventory_tweaks.swap_warning"),
	HELD("screen.kohs_inventory_tweaks.held_slot_marker"),
	KEYS("screen.kohs_inventory_tweaks.key_hints"),
	DURABILITY("screen.kohs_inventory_tweaks.durability_readout"),
	TOTALS("screen.kohs_inventory_tweaks.item_totals"),
	FLASH("screen.kohs_inventory_tweaks.slot_flash"),
	ANIMATIONS("screen.kohs_inventory_tweaks.remove_animations");

	final String key;

	InventoryTweakOption(final String key) {
		this.key = key;
	}

	boolean enabled(final InventoryTweaksConfig config) {
		return switch (this) {
			case FAST -> config.superFastInventory;
			case CENTER -> config.centerMouseFix;
			case POINTER -> config.shortcutsFollowPointer;
			case PIXEL -> config.pixelPerfectScale;
			case STEADY -> config.steadyTooltips;
			case RECIPE -> config.recipeBookLock;
			case TOTEM -> config.totemGuard;
			case SWAP -> config.swapWarning;
			case HELD -> config.heldSlotMarker;
			case KEYS -> config.keyHints;
			case DURABILITY -> config.durabilityReadout;
			case TOTALS -> config.itemTotals;
			case FLASH -> config.slotFlash;
			case ANIMATIONS -> config.reduceInventoryMotion;
		};
	}

	void set(final InventoryTweaksConfig config, final boolean enabled) {
		switch (this) {
			case FAST -> config.superFastInventory = enabled;
			case CENTER -> config.centerMouseFix = enabled;
			case POINTER -> config.shortcutsFollowPointer = enabled;
			case PIXEL -> config.pixelPerfectScale = enabled;
			case STEADY -> config.steadyTooltips = enabled;
			case RECIPE -> config.recipeBookLock = enabled;
			case TOTEM -> config.totemGuard = enabled;
			case SWAP -> config.swapWarning = enabled;
			case HELD -> config.heldSlotMarker = enabled;
			case KEYS -> config.keyHints = enabled;
			case DURABILITY -> config.durabilityReadout = enabled;
			case TOTALS -> config.itemTotals = enabled;
			case FLASH -> config.slotFlash = enabled;
			case ANIMATIONS -> config.reduceInventoryMotion = enabled;
		}
		config.activeProfile = InventoryTweaksConfig.ProfilePreset.CUSTOM;
	}

	boolean warnsOnEnable() {
		return this == CENTER || this == ANIMATIONS;
	}

	/** The feature domain whose compatibility state decides whether the switch works. */
	CompatibilityFeature feature() {
		return this == PIXEL ? CompatibilityFeature.GUI_SCALER : CompatibilityFeature.INVENTORY_TWEAKS;
	}
}
