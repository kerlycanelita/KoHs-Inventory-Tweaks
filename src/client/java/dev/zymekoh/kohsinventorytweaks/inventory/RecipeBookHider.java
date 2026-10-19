package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.RecipeBookType;

/**
 * Hide recipe book: the inventory, the crafting table and the furnaces show no recipe book button,
 * and their book stays shut, so a stray click can never open it and push the inventory aside.
 *
 * <p>Only what the screens show changes. The book setting the player saved is left as it was and
 * nothing is sent to the server: turning the option off brings the book back as it was left.</p>
 */
public final class RecipeBookHider {
	private RecipeBookHider() {
	}

	public static boolean hidden() {
		return ConfigStore.get().hideRecipeBook
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}

	/** Whether the recipe book of this kind will show: the player's own book, unless it is hidden. */
	public static boolean open(final LocalPlayer player, final RecipeBookType type) {
		return !hidden() && player.getRecipeBook().isOpen(type);
	}
}
