package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import java.util.function.Supplier;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla sizes the status effect panels from the screen's position and width. An inventory moved by a Next Gen
 * style or enlarged by the GUI Scaler leaves those numbers describing a place the panels are not in, so Vanilla is
 * lent the real ones while it measures: it then picks full panels, icons alone or the HUD's own icons by itself.
 */
public final class EffectsPlacement {
	private static final int COLUMN_GAP = 2;
	private static final int PANEL_SIZE = 32;
	private static final double SAME_SCALE = 0.0001;

	private EffectsPlacement() {
	}

	public static <T> T whileMeasuring(final AbstractContainerScreen<?> screen, final Supplier<T> vanilla) {
		int[] offset = offsetFor(screen);
		double scale = InventoryGuiScaler.appliedSurfaceScale(screen, ConfigStore.get());
		if (offset == null && Math.abs(scale - 1.0) < SAME_SCALE) {
			return vanilla.get();
		}
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		int left = access.kohsInventoryTweaks$getLeftPos();
		int top = access.kohsInventoryTweaks$getTopPos();
		int width = screen.width;
		int moveX = offset == null ? 0 : offset[0];
		int moveY = offset == null ? 0 : offset[1];
		int column = left + access.kohsInventoryTweaks$getImageWidth() + COLUMN_GAP + moveX;
		int visibleLeft = (int) Math.ceil(InventoryGuiScaler.toInventoryCoordinate(0, width, scale));
		int visibleRight = (int) Math.floor(InventoryGuiScaler.toInventoryCoordinate(width, width, scale));
		int visibleTop = (int) Math.ceil(InventoryGuiScaler.toInventoryCoordinate(0, screen.height, scale));
		int visibleBottom = (int) Math.floor(InventoryGuiScaler.toInventoryCoordinate(screen.height, screen.height, scale));
		access.kohsInventoryTweaks$setLeftPos(left + moveX + Math.max(0, visibleLeft - column));
		access.kohsInventoryTweaks$setTopPos(Mth.clamp(top + moveY, visibleTop, Math.max(visibleTop, visibleBottom - PANEL_SIZE)));
		screen.width = visibleRight;
		try {
			return vanilla.get();
		} finally {
			access.kohsInventoryTweaks$setLeftPos(left);
			access.kohsInventoryTweaks$setTopPos(top);
			screen.width = width;
		}
	}

	private static int @Nullable [] offsetFor(final AbstractContainerScreen<?> screen) {
		if (screen instanceof CreativeModeInventoryScreen creative) {
			return NextGenCreative.effectsOffset(creative);
		}
		return screen instanceof InventoryScreen inventory ? NextGenInventory.effectsOffset(inventory) : null;
	}
}
