package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;

/** Shared option metadata for rendering, narration, state changes and warnings. */
enum InventoryTweakOption {
	FAST("screen.kohs_inventory_tweaks.super_fast_inventory"),
	CENTER("screen.kohs_inventory_tweaks.center_mouse_fix"),
	ANIMATIONS("screen.kohs_inventory_tweaks.remove_animations");

	final String key;

	InventoryTweakOption(final String key) {
		this.key = key;
	}

	boolean enabled(final InventoryTweaksConfig config) {
		return switch (this) {
			case FAST -> config.superFastInventory;
			case CENTER -> config.centerMouseFix;
			case ANIMATIONS -> config.reduceInventoryMotion;
		};
	}

	void set(final InventoryTweaksConfig config, final boolean enabled) {
		switch (this) {
			case FAST -> config.superFastInventory = enabled;
			case CENTER -> config.centerMouseFix = enabled;
			case ANIMATIONS -> config.reduceInventoryMotion = enabled;
		}
		config.activeProfile = InventoryTweaksConfig.ProfilePreset.CUSTOM;
	}

	boolean warnsOnEnable() {
		return this == CENTER || this == ANIMATIONS;
	}
}
