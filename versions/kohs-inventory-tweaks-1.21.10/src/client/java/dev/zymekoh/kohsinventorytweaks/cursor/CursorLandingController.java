package dev.zymekoh.kohsinventorytweaks.cursor;

import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig.CursorPoint;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.MouseHandlerAccessor;
import dev.zymekoh.kohsinventorytweaks.inventory.InventoryGuiScaler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ChestMenu;
import org.lwjgl.glfw.GLFW;

public final class CursorLandingController {
	private static final long CENTER_GUARD_NANOS = 100_000_000L;
	private static final double CENTER_EVENT_TOLERANCE = 1.25;
	private static Screen openingScreen;
	private static CursorTarget openingTarget;
	private static Screen verificationScreen;
	private static boolean verificationPending;
	private static long centerGuardUntil;

	private CursorLandingController() {
	}

	public static void onScreenRequested(final Screen screen) {
		openingScreen = screen;
		openingTarget = classify(screen);
		verificationPending = false;
		verificationScreen = null;
		centerGuardUntil = 0L;
	}

	public static double[] overrideReleasePosition(final Minecraft minecraft) {
		Screen screen = minecraft == null ? null : minecraft.screen;
		CursorTarget target = screen == openingScreen ? openingTarget : classify(screen);
		return target == null || !shouldPlaceCursor(target)
			? null
			: resolvePhysicalPosition(minecraft, screen, target, false);
	}

	public static void onContainerScreenInitialized(final Minecraft minecraft, final Screen screen) {
		CursorTarget target = classify(screen);
		if (target == null || !shouldPlaceCursor(target)) {
			openingScreen = null;
			openingTarget = null;
			return;
		}

		warp(minecraft, resolvePhysicalPosition(minecraft, screen, target, true));
		if (isCenterMouseFixTarget(target)) {
			verificationPending = true;
			verificationScreen = screen;
			centerGuardUntil = System.nanoTime() + CENTER_GUARD_NANOS;
		}
		openingScreen = null;
		openingTarget = null;
	}

	public static void onClientTick(final Minecraft minecraft) {
		if (!verificationPending) {
			return;
		}

		verificationPending = false;
		if (minecraft == null || minecraft.screen != verificationScreen || System.nanoTime() > centerGuardUntil) {
			return;
		}

		CursorTarget target = classify(minecraft.screen);
		if (target != null && shouldPlaceCursor(target)) {
			warp(minecraft, resolvePhysicalPosition(minecraft, minecraft.screen, target, true));
		}
	}

	public static boolean recoverUnexpectedCenterEvent(final Minecraft minecraft, final double x, final double y) {
		if (minecraft == null
			|| minecraft.screen == null
			|| !ConfigStore.get().centerMouseFix
			|| System.nanoTime() > centerGuardUntil) {
			return false;
		}

		CursorTarget target = classify(minecraft.screen);
		if (target == null || !shouldPlaceCursor(target)) {
			return false;
		}

		Window window = minecraft.getWindow();
		double centerX = window.getScreenWidth() * 0.5;
		double centerY = window.getScreenHeight() * 0.5;
		double[] desired = resolvePhysicalPosition(minecraft, minecraft.screen, target, true);
		boolean desiredIsCenter = Math.abs(desired[0] - centerX) <= CENTER_EVENT_TOLERANCE
			&& Math.abs(desired[1] - centerY) <= CENTER_EVENT_TOLERANCE;
		boolean eventIsCenter = Math.abs(x - centerX) <= CENTER_EVENT_TOLERANCE
			&& Math.abs(y - centerY) <= CENTER_EVENT_TOLERANCE;
		if (desiredIsCenter || !eventIsCenter) {
			return false;
		}

		warp(minecraft, desired);
		return true;
	}

	private static void warp(final Minecraft minecraft, final double[] position) {
		if (minecraft == null || position == null) {
			return;
		}

		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setXpos(position[0]);
		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setYpos(position[1]);
		GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), position[0], position[1]);
	}

	private static double[] resolvePhysicalPosition(
		final Minecraft minecraft,
		final Screen screen,
		final CursorTarget target,
		final boolean initialized
	) {
		Window window = minecraft.getWindow();
		// A disabled custom landing target must remain truly vanilla. Center Mouse
		// Fix may still verify the player inventory's vanilla centered position, but
		// it never reuses a saved custom point while that target's switch is off.
		CursorPoint point = ConfigStore.get().isCursorEnabled(target)
			? ConfigStore.get().getPosition(target)
			: null;
		if (point == null) {
			return new double[] {window.getScreenWidth() * 0.5, window.getScreenHeight() * 0.5};
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
			if (initialized || screen.width > 0) {
				left = accessor.kohsInventoryTweaks$getLeftPos();
				top = accessor.kohsInventoryTweaks$getTopPos();
			}
		}

		double logicalX = left + point.x() * imageWidth;
		double logicalY = top + point.y() * imageHeight;
		if (screen instanceof InventoryScreen inventoryScreen) {
			double scale = InventoryGuiScaler.appliedScale(inventoryScreen, ConfigStore.get());
			logicalX = guiWidth * 0.5 + (logicalX - guiWidth * 0.5) * scale;
			logicalY = guiHeight * 0.5 + (logicalY - guiHeight * 0.5) * scale;
		}
		double x = logicalX * window.getScreenWidth() / Math.max(1.0, guiWidth);
		double y = logicalY * window.getScreenHeight() / Math.max(1.0, guiHeight);
		return new double[] {x, y};
	}

	private static boolean shouldPlaceCursor(final CursorTarget target) {
		return ConfigStore.get().isCursorEnabled(target) || isCenterMouseFixTarget(target);
	}

	private static boolean isCenterMouseFixTarget(final CursorTarget target) {
		return target == CursorTarget.INVENTORY && ConfigStore.get().centerMouseFix;
	}

	private static CursorTarget classify(final Screen screen) {
		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
			return CursorTarget.INVENTORY;
		}
		if (!(screen instanceof ContainerScreen) || !(screen instanceof MenuAccess<?> access)) {
			return null;
		}

		String key = "";
		if (screen.getTitle().getContents() instanceof TranslatableContents translatable) {
			key = translatable.getKey();
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
