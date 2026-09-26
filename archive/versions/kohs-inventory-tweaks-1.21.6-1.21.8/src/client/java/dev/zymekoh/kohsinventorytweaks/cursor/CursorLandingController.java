package dev.zymekoh.kohsinventorytweaks.cursor;

import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
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
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ChestMenu;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.DoubleBuffer;

public final class CursorLandingController {
	private static final double CURSOR_POSITION_EPSILON = 0.5;
	private static Screen openingScreen;
	private static CursorTarget openingTarget;
	private static Screen releasePlacementScreen;
	private static double[] releasePlacementPosition;
	private static volatile boolean suppressNativeCursorCentering;
	private static boolean rawInputLookupComplete;
	private static Method rawInputGetHandler;
	private static Method rawInputTick;
	private static Method rawInputResetDeltas;

	private CursorLandingController() {
	}

	public static void onScreenRequested(final Screen screen) {
		suppressNativeCursorCentering = screen != null
			&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CURSOR_LANDING);
		openingScreen = screen;
		openingTarget = classify(screen);
		releasePlacementScreen = null;
		releasePlacementPosition = null;
	}

	public static void onMouseGrabRequested() {
		suppressNativeCursorCentering = false;
	}

	public static boolean shouldSuppressNativeCursorCentering() {
		return suppressNativeCursorCentering;
	}

	public static double[] overrideReleasePosition(final Minecraft minecraft) {
		Screen screen = minecraft == null ? null : minecraft.screen;
		CursorTarget target = screen == openingScreen ? openingTarget : classify(screen);
		if (target == null || !shouldPlaceCursor(target)) {
			return null;
		}

		// Raw Input Buffer changes its screen/game-focus flag on its own client tick.
		// If an inventory opens between two of those ticks, its raw-input thread may
		// still center the Windows cursor once. Synchronizing its public handler here
		// prevents that stale center operation without adding a hard dependency.
		synchronizeRawInputScreenState();

		double[] position = resolvePhysicalPosition(minecraft, screen, target, false);
		releasePlacementScreen = screen;
		releasePlacementPosition = position;
		return position;
	}

	/**
	 * Commits the same release target after GLFW has switched from captured to
	 * visible cursor mode. Under a native raw-input owner, the cursor-position
	 * write performed while captured may update only GLFW's virtual coordinates;
	 * this conditional commit keeps the physical pointer and Minecraft state in
	 * agreement without scheduling a later correction or pinning the cursor.
	 */
	public static void onMouseReleased(final Minecraft minecraft) {
		if (minecraft == null
			|| minecraft.screen != releasePlacementScreen
			|| releasePlacementPosition == null) {
			return;
		}
		warp(minecraft, releasePlacementPosition);
	}

	public static void onContainerScreenInitialized(final Minecraft minecraft, final Screen screen) {
		CursorTarget target = classify(screen);
		if (target == null || !shouldPlaceCursor(target)) {
			clearOpeningState();
			return;
		}

		if (screen == releasePlacementScreen && releasePlacementPosition != null) {
			// Normally onMouseReleased already committed this exact point after GLFW
			// exposed the cursor. Rechecking the physical coordinates here is a no-op
			// unless another native input owner changed them during screen setup.
			warp(minecraft, releasePlacementPosition);
		} else {
			// releaseMouse is skipped when one GUI replaces another. In that case the
			// initialized layout is the only placement point. A screen opened from
			// gameplay retains the exact release target stored above.
			synchronizeRawInputScreenState();
			warp(minecraft, resolvePhysicalPosition(minecraft, screen, target, true));
		}
		clearOpeningState();
	}

	private static void clearOpeningState() {
		openingScreen = null;
		openingTarget = null;
		releasePlacementScreen = null;
		releasePlacementPosition = null;
	}

	private static void synchronizeRawInputScreenState() {
		if (!FabricLoader.getInstance().isModLoaded("rawinputbuffer")) {
			return;
		}

		try {
			if (!rawInputLookupComplete) {
				Class<?> rawInputClass = Class.forName("walksy.rawinput.RawInput");
				Class<?> handlerClass = Class.forName("walksy.rawinput.RawInputHandler");
				rawInputGetHandler = rawInputClass.getMethod("getInputHandler");
				rawInputTick = handlerClass.getMethod("tick");
				rawInputResetDeltas = handlerClass.getMethod("resetDeltas");
				rawInputLookupComplete = true;
			}
			if (rawInputGetHandler != null && rawInputTick != null && rawInputResetDeltas != null) {
				Object handler = rawInputGetHandler.invoke(null);
				if (handler != null) {
					rawInputTick.invoke(handler);
					rawInputResetDeltas.invoke(handler);
				}
			}
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
			// Raw Input Buffer may change its internals in a future release. Cursor
			// landing remains fully functional; only this optional race workaround is
			// skipped when its public API no longer matches.
			rawInputLookupComplete = true;
			rawInputGetHandler = null;
			rawInputTick = null;
			rawInputResetDeltas = null;
		}
	}

	private static void warp(final Minecraft minecraft, final double[] position) {
		if (minecraft == null || position == null) {
			return;
		}

		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setXpos(position[0]);
		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setYpos(position[1]);

		long handle = minecraft.getWindow().getWindow();
		try (MemoryStack stack = MemoryStack.stackPush()) {
			DoubleBuffer currentX = stack.mallocDouble(1);
			DoubleBuffer currentY = stack.mallocDouble(1);
			GLFW.glfwGetCursorPos(handle, currentX, currentY);
			if (Math.abs(currentX.get(0) - position[0]) <= CURSOR_POSITION_EPSILON
				&& Math.abs(currentY.get(0) - position[1]) <= CURSOR_POSITION_EPSILON) {
				return;
			}
		}
		GLFW.glfwSetCursorPos(handle, position[0], position[1]);
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

		double logicalX = left + point.x() * imageWidth;
		double logicalY = top + point.y() * imageHeight;
		if (screen instanceof InventoryScreen inventoryScreen) {
			double scale = InventoryGuiScaler.appliedScale(inventoryScreen, ConfigStore.get());
			logicalX = guiWidth * 0.5 + (logicalX - guiWidth * 0.5) * scale;
			logicalY = guiHeight * 0.5 + (logicalY - guiHeight * 0.5) * scale;
		} else {
			double scale = InventoryGuiScaler.appliedContainerScale(screen, ConfigStore.get());
			logicalX = guiWidth * 0.5 + (logicalX - guiWidth * 0.5) * scale;
			logicalY = guiHeight * 0.5 + (logicalY - guiHeight * 0.5) * scale;
		}
		double x = logicalX * window.getScreenWidth() / Math.max(1.0, guiWidth);
		double y = logicalY * window.getScreenHeight() / Math.max(1.0, guiHeight);
		return new double[] {x, y};
	}

	private static boolean shouldPlaceCursor(final CursorTarget target) {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CURSOR_LANDING)
			&& (ConfigStore.get().isCursorEnabled(target) || isCenterMouseFixTarget(target));
	}

	private static boolean isCenterMouseFixTarget(final CursorTarget target) {
		return target == CursorTarget.INVENTORY && ConfigStore.get().centerMouseFix;
	}

	private static CursorTarget classify(final Screen screen) {
		if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
			return CursorTarget.INVENTORY;
		}
		if (screen instanceof ShulkerBoxScreen) {
			return CursorTarget.SHULKER;
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
