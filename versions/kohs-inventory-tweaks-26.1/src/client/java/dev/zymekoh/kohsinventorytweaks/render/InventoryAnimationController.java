package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;

/** Optional cosmetic motion reduction; progress, recipe choices and glint stay Vanilla-owned. */
public final class InventoryAnimationController {
	private InventoryAnimationController() {
	}

	public static boolean reduceMotionEnabled() {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)
			&& ConfigStore.get().reduceInventoryMotion;
	}
}
