package dev.zymekoh.kohsinventorytweaks.cursor;

import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.CursorPoint;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.jspecify.annotations.Nullable;

/**
 * Where the pointer should be when a screen opens: the window centre for Center
 * Mouse Fix, or a stored Cursor Landing point resolved through the inventory's
 * real layout and scale. Pure position maths; nothing here moves the pointer.
 */
final class CursorPlacement {
	private CursorPlacement() {
	}

	static double[] resolve(
		final Minecraft minecraft,
		final Screen screen,
		final CursorTarget target,
		final boolean initialized
	) {
		Window window = minecraft.getWindow();
		// A disabled target never reuses its saved custom point.
		CursorPoint point = customPoint(target);
		if (point == null) {
			// Match releaseMouse's integer center, including odd window dimensions.
			return new double[] {window.getScreenWidth() / 2, window.getScreenHeight() / 2};
		}

		int guiWidth = window.getGuiScaledWidth();
		int guiHeight = window.getGuiScaledHeight();
		int imageWidth = target.previewWidth();
		int imageHeight = target.previewHeight();
		int left = (guiWidth - imageWidth) / 2;
		int top = (guiHeight - imageHeight) / 2;
		if (screen instanceof AbstractContainerScreenAccessor accessor) {
			imageWidth = accessor.kohsInventoryTweaks$getImageWidth();
			imageHeight = accessor.kohsInventoryTweaks$getImageHeight();
			if (initialized) {
				left = accessor.kohsInventoryTweaks$getLeftPos();
				top = accessor.kohsInventoryTweaks$getTopPos();
			} else if (screen instanceof InventoryScreen inventoryScreen
				&& guiWidth >= 379
				&& minecraft.player != null
				&& minecraft.player.getRecipeBook().isOpen(inventoryScreen.getMenu().getRecipeBookType())) {
				// AbstractRecipeBookScreen shifts the player inventory during init when
				// its book is open. Reproducing that vanilla calculation here avoids a
				// visible second jump after initialization.
				left = 177 + (guiWidth - imageWidth - 200) / 2;
			}
		}

		// Saved fractions are authored against the preview's inclusive pixel range
		// (0..width - 1 / 0..height - 1). Use that same range at runtime so an
		// edge click cannot drift one logical pixel outside the inventory.
		double localX = point.x() * Math.max(0, imageWidth - 1);
		double localY = point.y() * Math.max(0, imageHeight - 1);
		double logicalX = left + localX;
		double logicalY = top + localY;
		// Minecraft releases the mouse before it initializes the screen, so the
		// screen still reports a zero size here. The window's GUI-scaled dimensions
		// are the ones the layout will use, and they match Screen#width once the
		// screen is initialized.
		double scale = screen instanceof InventoryScreen inventoryScreen
			? InventoryGuiScaler.appliedScale(inventoryScreen, guiWidth, guiHeight, ConfigStore.get())
			: InventoryGuiScaler.appliedContainerScale(screen, guiWidth, guiHeight, ConfigStore.get());
		logicalX = guiWidth * 0.5 + (logicalX - guiWidth * 0.5) * scale;
		logicalY = guiHeight * 0.5 + (logicalY - guiHeight * 0.5) * scale;
		double x = logicalX * window.getScreenWidth() / Math.max(1.0, guiWidth);
		double y = logicalY * window.getScreenHeight() / Math.max(1.0, guiHeight);
		// GLFW's desktop cursor has whole-pixel precision on Windows. Use the same
		// pixel for Minecraft's hit testing and the visible pointer; fractions could
		// otherwise trigger a redundant correction and disagree at slot edges.
		return new double[] {Math.round(x), Math.round(y)};
	}

	static boolean shouldPlace(final CursorTarget target) {
		return isCenterMouseFixTarget(target) || customPoint(target) != null;
	}

	static @Nullable CursorPoint customPoint(final CursorTarget target) {
		InventoryTweaksConfig config = ConfigStore.get();
		return isCustomCursorLandingAvailable() && config.isCursorEnabled(target)
			? config.getPosition(target)
			: null;
	}

	static boolean isCenterMouseFixTarget(final CursorTarget target) {
		return target == CursorTarget.INVENTORY && ConfigStore.get().centerMouseFix
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS);
	}

	static boolean isCustomCursorLandingAvailable() {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CURSOR_LANDING);
	}
}
