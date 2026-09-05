package dev.zymekoh.kohsinventorytweaks.render;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;

/**
 * Keeps inventory-only animation overrides scoped to the item currently being
 * extracted. The scope deliberately excludes the HUD, held items, entities and
 * world rendering.
 */
public final class InventoryAnimationController {
	private static final ThreadLocal<Integer> INVENTORY_ITEM_DEPTH = ThreadLocal.withInitial(() -> 0);

	private InventoryAnimationController() {
	}

	public static void beginInventoryItem() {
		if (suppressAllInventoryAnimations()) {
			INVENTORY_ITEM_DEPTH.set(INVENTORY_ITEM_DEPTH.get() + 1);
		}
	}

	public static void endInventoryItem() {
		int depth = INVENTORY_ITEM_DEPTH.get();
		if (depth <= 1) {
			INVENTORY_ITEM_DEPTH.remove();
		} else {
			INVENTORY_ITEM_DEPTH.set(depth - 1);
		}
	}

	public static boolean suppressAnimatedFoil() {
		return INVENTORY_ITEM_DEPTH.get() > 0 && suppressAllInventoryAnimations();
	}

	public static boolean suppressAllInventoryAnimations() {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)
			&& ConfigStore.get().removeAllInventoryAnimations;
	}
}
