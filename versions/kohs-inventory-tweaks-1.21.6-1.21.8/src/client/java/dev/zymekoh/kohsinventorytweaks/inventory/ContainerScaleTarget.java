package dev.zymekoh.kohsinventorytweaks.inventory;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ChestMenu;

public enum ContainerScaleTarget {
	CHEST_SINGLE("screen.kohs_inventory_tweaks.target.chest_single", 176, 168, 3),
	CHEST_DOUBLE("screen.kohs_inventory_tweaks.target.chest_double", 176, 222, 6),
	SHULKER("screen.kohs_inventory_tweaks.target.shulker", 176, 167, 3),
	BARREL("screen.kohs_inventory_tweaks.target.barrel", 176, 168, 3),
	ENDER_CHEST("screen.kohs_inventory_tweaks.target.ender_chest", 176, 168, 3);

	private final String translationKey;
	private final int width;
	private final int height;
	private final int rows;

	ContainerScaleTarget(final String translationKey, final int width, final int height, final int rows) {
		this.translationKey = translationKey;
		this.width = width;
		this.height = height;
		this.rows = rows;
	}

	public String translationKey() {
		return this.translationKey;
	}

	public int previewWidth() {
		return this.width;
	}

	public int previewHeight() {
		return this.height;
	}

	public int rows() {
		return this.rows;
	}

	public static ContainerScaleTarget classify(final Screen screen) {
		if (screen instanceof ShulkerBoxScreen) {
			return SHULKER;
		}
		if (!(screen instanceof ContainerScreen) || !(screen instanceof MenuAccess<?> access)) {
			return null;
		}

		String key = "";
		if (screen.getTitle().getContents() instanceof TranslatableContents translatable) {
			key = translatable.getKey().toLowerCase(java.util.Locale.ROOT);
		}
		if (key.contains("enderchest")) {
			return ENDER_CHEST;
		}
		if (key.contains("barrel")) {
			return BARREL;
		}
		if (access.getMenu() instanceof ChestMenu chestMenu && chestMenu.getRowCount() >= 6) {
			return CHEST_DOUBLE;
		}
		return CHEST_SINGLE;
	}
}

