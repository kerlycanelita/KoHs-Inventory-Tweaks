package dev.zymekoh.kohsinventorytweaks.screen;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.ui.ZMascot;
import org.jspecify.annotations.Nullable;

/**
 * The Inventory Tweaks page as a tree, in page order: a sub-option follows its parent
 * and only works while the parent is on. Shared metadata for rendering, narration,
 * state changes and warnings.
 */
enum InventoryTweakOption {
	FAST("screen.kohs_inventory_tweaks.super_fast_inventory"),
	POINTER("screen.kohs_inventory_tweaks.shortcuts_follow_pointer"),
	CENTER("screen.kohs_inventory_tweaks.center_mouse_fix"),
	ANIMATIONS("screen.kohs_inventory_tweaks.remove_animations"),
	MASCOT("screen.kohs_inventory_tweaks.mascot");

	final String key;

	InventoryTweakOption(final String key) {
		this.key = key;
	}

	/** The option this one refines, or null for a top-level option. */
	@Nullable InventoryTweakOption parent() {
		return this == POINTER ? FAST : null;
	}

	boolean enabled(final InventoryTweaksConfig config) {
		return switch (this) {
			case FAST -> config.superFastInventory;
			case POINTER -> config.shortcutsFollowPointer;
			case CENTER -> config.centerMouseFix;
			case ANIMATIONS -> config.reduceInventoryMotion;
			// The mascot keeps its own file: it is never part of a profile or a working copy.
			case MASCOT -> ZMascot.prefs().enabled;
		};
	}

	void set(final InventoryTweaksConfig config, final boolean enabled) {
		if (this == MASCOT) {
			if (enabled != ZMascot.prefs().enabled) {
				ZMascot.setEnabled(enabled);
			}
			return;
		}
		switch (this) {
			case FAST -> config.superFastInventory = enabled;
			case POINTER -> config.shortcutsFollowPointer = enabled;
			case CENTER -> config.centerMouseFix = enabled;
			case ANIMATIONS -> config.reduceInventoryMotion = enabled;
			default -> {
			}
		}
		config.activeProfile = InventoryTweaksConfig.ProfilePreset.CUSTOM;
	}

	boolean warnsOnEnable() {
		return this == CENTER || this == ANIMATIONS;
	}

	/** Whether the inventory tweaks are compatible here; the mascot never depends on it. */
	boolean available() {
		return this == MASCOT || CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}

	/** Whether its switch works now: available, and its parent, if it has one, on. */
	boolean usable(final InventoryTweaksConfig config) {
		InventoryTweakOption parent = this.parent();
		return this.available() && (parent == null || parent.enabled(config));
	}
}
