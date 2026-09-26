package dev.zymekoh.kohsinventorydebug;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Shared screen classification used by the recorder and the QA macros. */
public final class InventoryScreenClassifier {
	private InventoryScreenClassifier() {
	}

	public static boolean isPlayerInventory(final Screen screen) {
		return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen;
	}
}
