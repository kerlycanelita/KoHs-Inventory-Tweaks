package dev.zymekoh.kohsinventorytweaks.cursor;

import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ChestMenu;
import org.jspecify.annotations.Nullable;

/** Which Cursor Landing target a screen is, or null for screens it never places on. */
final class CursorScreens {
	private CursorScreens() {
	}

	static @Nullable CursorTarget classify(final @Nullable Screen screen) {
		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
			return CursorTarget.INVENTORY;
		}
		if (screen instanceof ShulkerBoxScreen) {
			return CursorTarget.SHULKER;
		}
		if (!(screen instanceof ContainerScreen) || !(screen instanceof MenuAccess<?> access)) {
			return null;
		}

		// Folded the same way ContainerScaleTarget folds it. The two classifiers have
		// to agree on every screen: this one picks which stored point to land on, and
		// that one picks the scale the landing is computed through.
		String key = "";
		if (screen.getTitle().getContents() instanceof TranslatableContents translatable) {
			key = translatable.getKey().toLowerCase(Locale.ROOT);
		}
		if (key.contains("enderchest")) {
			return CursorTarget.ENDER_CHEST;
		}
		if (key.contains("barrel")) {
			return CursorTarget.BARREL;
		}

		if (access.getMenu() instanceof ChestMenu chestMenu && chestMenu.getRowCount() >= 6) {
			return CursorTarget.CHEST_DOUBLE;
		}
		return CursorTarget.CHEST_SINGLE;
	}
}
