package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractRecipeBookScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

public final class InventoryGuiScaler {
	public static final double MINIMUM_SCALE = 0.65;
	public static final double MAXIMUM_SCALE = 3.15;
	/** 100% is fixed to the physical size Vanilla uses at GUI Scale 2x. */
	public static final double PHYSICAL_REFERENCE_GUI_SCALE = 2.0;
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int INVENTORY_WITH_RECIPE_BOOK_WIDTH = 379;
	private static final int SCREEN_MARGIN = 8;
	private static final double SCALE_EPSILON = 0.0001;

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
		return Math.max(0.05, Math.min(
			MAXIMUM_SCALE,
			Math.min(horizontalFit, verticalFit) * currentGuiScale() / PHYSICAL_REFERENCE_GUI_SCALE
		));
	}

	public static double currentGuiScale() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.getWindow() == null) {
			return 1.0;
		}
		return Math.max(1.0, minecraft.getWindow().getGuiScale());
	}

	/** Converts the fixed 2x-reference percentage into the logical pose scale Minecraft expects. */
	public static double toSurfaceScale(final double physicalScale) {
		return physicalScale * PHYSICAL_REFERENCE_GUI_SCALE / currentGuiScale();
	}

	public static double configuredPhysicalScale(
		final int screenWidth,
		final int screenHeight,
		final InventoryTweaksConfig config
	) {
		if (config == null) {
			return 1.0;
		}
		return Math.min(clampConfiguredScale(config.inventoryGuiScale), maximumScaleFor(screenWidth, screenHeight));
	}

	public static double configuredContainerPhysicalScale(
		final int screenWidth,
		final int screenHeight,
		final InventoryTweaksConfig config,
		final ContainerScaleTarget target
	) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.GUI_SCALER)
			|| config == null || !config.inventoryGuiScalerEnabled || target == null) {
			return 1.0;
		}
		if (config.containerProfilesEnabled && !config.isContainerScaleEnabled(target)) {
			return 1.0;
		}
		double requestedScale = config.containerProfilesEnabled
			? config.containerScale(target)
			: config.inventoryGuiScale;
		return Math.min(
			clampConfiguredScale(requestedScale),
			maximumScaleFor(screenWidth, screenHeight, target.previewWidth(), target.previewHeight())
		);
	}

	public static double configuredContainerScale(
		final int screenWidth,
		final int screenHeight,
		final InventoryTweaksConfig config,
		final ContainerScaleTarget target
	) {
		return toSurfaceScale(configuredContainerPhysicalScale(screenWidth, screenHeight, config, target));
	}

	public static double appliedContainerScale(final Screen screen, final InventoryTweaksConfig config) {
		return appliedContainerScale(screen, screen.width, screen.height, config);
	}

	public static double appliedContainerScale(
		final Screen screen,
		final int screenWidth,
		final int screenHeight,
		final InventoryTweaksConfig config
	) {
		ContainerScaleTarget target = ContainerScaleTarget.classify(screen);
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.GUI_SCALER)
			|| config == null
			|| target == null
			|| !config.affectAllContainers && !config.containerProfilesEnabled) {
			return 1.0;
		}
		return configuredContainerScale(screenWidth, screenHeight, config, target);
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
		return toSurfaceScale(configuredPhysicalScale(screenWidth, screenHeight, config));
	}

	public static double appliedScale(final InventoryScreen screen, final InventoryTweaksConfig config) {
		return appliedScale(screen, screen.width, screen.height, config);
	}

	public static double appliedSurfaceScale(final Screen screen, final InventoryTweaksConfig config) {
		return screen instanceof InventoryScreen inventoryScreen
			? appliedScale(inventoryScreen, config)
			: appliedContainerScale(screen, config);
	}

	public static double appliedScale(
		final InventoryScreen screen,
		final int screenWidth,
		final int screenHeight,
		final InventoryTweaksConfig config
	) {
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.GUI_SCALER)
			|| config == null || !config.inventoryGuiScalerEnabled) {
			return 1.0;
		}
		boolean recipeBookVisible = ((AbstractRecipeBookScreenAccessor) screen)
			.kohsInventoryTweaks$getRecipeBookComponent()
			.isVisible();
		double maximum = maximumScaleFor(screenWidth, screenHeight);
		if (recipeBookVisible) {
			double recipeBookFit = Math.max(1, screenWidth - SCREEN_MARGIN * 2)
				/ (double) INVENTORY_WITH_RECIPE_BOOK_WIDTH;
			maximum = Math.max(0.05, Math.min(
				maximum,
				recipeBookFit * currentGuiScale() / PHYSICAL_REFERENCE_GUI_SCALE
			));
		}
		return toSurfaceScale(Math.min(clampConfiguredScale(config.inventoryGuiScale), maximum));
	}

	public static double toInventoryCoordinate(final double coordinate, final int screenSize, final double scale) {
		if (Math.abs(scale - 1.0) < SCALE_EPSILON) {
			return coordinate;
		}
		double center = screenSize * 0.5;
		return center + (coordinate - center) / scale;
	}

	public static MouseButtonEvent toInventoryEvent(
		final MouseButtonEvent event,
		final int screenWidth,
		final int screenHeight,
		final double scale
	) {
		if (Math.abs(scale - 1.0) < SCALE_EPSILON) {
			return event;
		}
		return new MouseButtonEvent(
			toInventoryCoordinate(event.x(), screenWidth, scale),
			toInventoryCoordinate(event.y(), screenHeight, scale),
			event.buttonInfo()
		);
	}
}
