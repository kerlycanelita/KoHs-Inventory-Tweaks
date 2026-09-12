package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;

/** Shared option metadata for rendering, narration, state changes and warnings. */
enum InventoryTweakOption {
	FAST("screen.kohs_inventory_tweaks.super_fast_inventory", Category.RESPONSE),
	HELD_MOUSE("screen.kohs_inventory_tweaks.fast_held_mouse", Category.RESPONSE),
	KEY_REPEAT("screen.kohs_inventory_tweaks.inventory_key_repeat", Category.RESPONSE),
	CENTER("screen.kohs_inventory_tweaks.center_mouse_fix", Category.CURSOR),
	SLOT_TARGET("screen.kohs_inventory_tweaks.immediate_slot_targeting", Category.CURSOR),
	ANIMATIONS("screen.kohs_inventory_tweaks.remove_animations", Category.VISUALS);

	enum Category {
		RESPONSE("screen.kohs_inventory_tweaks.tweaks.response"),
		CURSOR("screen.kohs_inventory_tweaks.tweaks.cursor"),
		VISUALS("screen.kohs_inventory_tweaks.tweaks.visuals");

		final String key;
		Category(final String key) { this.key = key; }
	}

	final String key;
	final Category category;

	InventoryTweakOption(final String key, final Category category) {
		this.key = key;
		this.category = category;
	}

	boolean enabled(final InventoryTweaksConfig config) {
		return switch (this) {
			case FAST -> config.superFastInventory;
			case HELD_MOUSE -> config.fastInventoryWhileMouseHeld;
			case KEY_REPEAT -> config.suppressInventoryKeyRepeats;
			case CENTER -> config.centerMouseFix;
			case SLOT_TARGET -> config.immediateSlotTargeting;
			case ANIMATIONS -> config.removeAllInventoryAnimations;
		};
	}

	void set(final InventoryTweaksConfig config, final boolean enabled) {
		switch (this) {
			case FAST -> config.superFastInventory = enabled;
			case HELD_MOUSE -> config.fastInventoryWhileMouseHeld = enabled;
			case KEY_REPEAT -> config.suppressInventoryKeyRepeats = enabled;
			case CENTER -> config.centerMouseFix = enabled;
			case SLOT_TARGET -> config.immediateSlotTargeting = enabled;
			case ANIMATIONS -> config.removeAllInventoryAnimations = enabled;
		}
		config.activeProfile = InventoryTweaksConfig.ProfilePreset.CUSTOM;
	}

	boolean warnsOnEnable() {
		return this == HELD_MOUSE || this == ANIMATIONS;
	}
}
