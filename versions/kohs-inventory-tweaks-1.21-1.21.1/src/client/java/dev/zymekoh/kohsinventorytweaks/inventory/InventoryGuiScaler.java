package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;

public final class InventoryGuiScaler {
	public static final double MINIMUM_SCALE = 0.65;
	public static final double MAXIMUM_SCALE = 1.75;
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int INVENTORY_WITH_RECIPE_BOOK_WIDTH = 379;
	private static final int SCREEN_MARGIN = 8;

	private InventoryGuiScaler() {
	}

	public static double clampConfiguredScale(final double scale) {
		if (!Double.isFinite(scale)) {
			return 1.0;
		}
		return Math.max(MINIMUM_SCALE, Math.min(MAXIMUM_SCALE, scale));
	}

	public static double maximumScaleFor(final int screenWidth, final int screenHeight) {
		return maximumScaleFor(screenWidth, screenHeight, INVENTORY_WIDTH, INVENTORY_HEIGHT);
	}

	public static double maximumScaleFor(
		final int screenWidth,
		final int screenHeight,
		final int contentWidth,
		final int contentHeight
	) {
		double horizontalFit = Math.max(1, screenWidth - SCREEN_MARGIN * 2) / (double) Math.max(1, contentWidth);
		double verticalFit = Math.max(1, screenHeight - SCREEN_MARGIN * 2) / (double) Math.max(1, contentHeight);
		return Math.max(MINIMUM_SCALE, Math.min(MAXIMUM_SCALE, Math.min(horizontalFit, verticalFit)));
	}

	public static double configuredContainerScale(
		final int screenWidth,
		final int screenHeight,
		final InventoryTweaksConfig config,
		final ContainerScaleTarget target
	) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.GUI_SCALER)
			|| config == null || !config.inventoryGuiScalerEnabled || target == null) {
			return 1.0;
		}
		return Math.min(
			clampConfiguredScale(config.inventoryGuiScale),
			maximumScaleFor(screenWidth, screenHeight, target.previewWidth(), target.previewHeight())
		);
	}

	public static double appliedContainerScale(final Screen screen, final InventoryTweaksConfig config) {
		ContainerScaleTarget target = ContainerScaleTarget.classify(screen);
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.GUI_SCALER)
			|| config == null || !config.affectAllContainers || target == null) {
			return 1.0;
		}
		return configuredContainerScale(screen.width, screen.height, config, target);
	}

	public static double appliedScale(
		final int screenWidth,
		final int screenHeight,
		final InventoryTweaksConfig config
	) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.GUI_SCALER)
			|| config == null || !config.inventoryGuiScalerEnabled) {
			return 1.0;
		}
		return Math.min(clampConfiguredScale(config.inventoryGuiScale), maximumScaleFor(screenWidth, screenHeight));
	}

	public static double appliedScale(final InventoryScreen screen, final InventoryTweaksConfig config) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.GUI_SCALER)
			|| config == null || !config.inventoryGuiScalerEnabled) {
			return 1.0;
		}
		boolean recipeBookVisible = screen.getRecipeBookComponent().isVisible();
		double maximum = maximumScaleFor(screen.width, screen.height);
		if (recipeBookVisible) {
			double recipeBookFit = Math.max(1, screen.width - SCREEN_MARGIN * 2)
				/ (double) INVENTORY_WITH_RECIPE_BOOK_WIDTH;
			maximum = Math.max(MINIMUM_SCALE, Math.min(maximum, recipeBookFit));
		}
		return Math.min(clampConfiguredScale(config.inventoryGuiScale), maximum);
	}

	public static double toInventoryCoordinate(final double coordinate, final int screenSize, final double scale) {
		if (Math.abs(scale - 1.0) < 0.0001) {
			return coordinate;
		}
		double center = screenSize * 0.5;
		return center + (coordinate - center) / scale;
	}

}
