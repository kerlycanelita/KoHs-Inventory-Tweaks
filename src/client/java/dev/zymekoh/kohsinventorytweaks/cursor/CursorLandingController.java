package dev.zymekoh.kohsinventorytweaks.cursor;

import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.InventoryTweaksConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;
import java.util.Locale;

public final class CursorLandingController {
	private static final double CURSOR_POSITION_EPSILON = 0.5;
	/** Hotbar and main storage. Armor sits at 36-39 and the offhand at 40. */
	private static final int LAST_MAIN_INVENTORY_SLOT = 35;
	private static @Nullable Screen openingScreen;
	private static @Nullable CursorTarget openingTarget;

	private CursorLandingController() {
	}

	public static void onScreenRequested(final @Nullable Screen screen) {
		if (!isScreenHandlingAvailable(screen)) {
			clearAllState();
			return;
		}
		openingScreen = screen;
		openingTarget = classify(screen);
	}

	public static @Nullable double[] overrideReleasePosition(final Minecraft minecraft) {
		Screen screen = minecraft == null ? null : minecraft.screen;
		// A later release/refocus is not another inventory opening. Never re-arm
		// custom landing merely because the same inventory is still on screen.
		if (screen == null || screen != openingScreen || !canPositionCursor(minecraft)) {
			return null;
		}
		CursorTarget target = openingTarget;
		if (target == null || !shouldPlaceCursor(target)) {
			return null;
		}

		// A custom target replaces Vanilla's release-time center so the cursor never
		// flashes through an unrelated point. Center Mouse Fix deliberately returns
		// null here and lets Vanilla perform its normal release-time center.
		return customPoint(target) == null && landingItem(target) == null
			? null
			: resolvePhysicalPosition(minecraft, screen, target, false);
	}

	/**
	 * Finalizes one cursor placement after {@link Minecraft#setScreen(Screen)} has
	 * completed the whole synchronous opening transaction.
	 *
	 * <p>This is still the same input event/frame: no tick, render or scheduled
	 * task is crossed. It runs after Vanilla mouse release and the screen layout.
	 * The final read-back corrects a mismatch observed at that boundary, without
	 * attempting to fight another component that writes afterwards. Once this
	 * method returns, real player movement owns the cursor again; there is no
	 * render-loop correction and therefore no dragging effect.</p>
	 */
	public static void onScreenOpened(final Minecraft minecraft, final @Nullable Screen requestedScreen) {
		Screen screen = minecraft == null ? null : minecraft.screen;
		if (screen == null || screen != requestedScreen || screen != openingScreen) {
			clearAllState();
			return;
		}
		if (!isScreenHandlingAvailable(screen) || !canPositionCursor(minecraft)) {
			clearAllState();
			return;
		}

		CursorTarget target = openingTarget != null ? openingTarget : classify(screen);
		if (target == null || !shouldPlaceCursor(target)) {
			clearOpeningState();
			return;
		}

		double[] placement = resolvePhysicalPosition(minecraft, screen, target, true);
		warp(minecraft, placement);
		// A Vanilla fallback can also open before handleAccumulatedMovement. Deltas
		// sampled before this synchronous landing belong to the old screen/camera,
		// not to a drag in the new inventory. Future callbacks remain untouched.
		MouseHandlerAccessor mouse = (MouseHandlerAccessor) minecraft.mouseHandler;
		mouse.kohsInventoryTweaks$setAccumulatedDX(0.0);
		mouse.kohsInventoryTweaks$setAccumulatedDY(0.0);
		clearOpeningState();
	}

	private static boolean matches(final double[] current, final double[] expected) {
		return Math.abs(current[0] - expected[0]) <= CURSOR_POSITION_EPSILON
			&& Math.abs(current[1] - expected[1]) <= CURSOR_POSITION_EPSILON;
	}

	private static boolean canPositionCursor(final Minecraft minecraft) {
		return minecraft != null && minecraft.isWindowActive()
			&& minecraft.getWindow().isFocused() && !minecraft.getWindow().isMinimized();
	}

	private static double @Nullable [] pointerPosition(final Minecraft minecraft) {
		if (minecraft == null) {
			return null;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			DoubleBuffer x = stack.mallocDouble(1);
			DoubleBuffer y = stack.mallocDouble(1);
			GLFW.glfwGetCursorPos(minecraft.getWindow().handle(), x, y);
			return new double[] {x.get(0), y.get(0)};
		}
	}

	private static void clearOpeningState() {
		openingScreen = null;
		openingTarget = null;
	}

	private static void clearAllState() {
		clearOpeningState();
	}

	private static void warp(final Minecraft minecraft, final double[] position) {
		if (minecraft == null || position == null) {
			return;
		}
		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setXpos(position[0]);
		((MouseHandlerAccessor) minecraft.mouseHandler).kohsInventoryTweaks$setYpos(position[1]);

		double[] current = pointerPosition(minecraft);
		if (current != null && matches(current, position)) {
			return;
		}
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
		CursorPoint point = customPoint(target);
		Slot itemSlot = landingSlot(minecraft, screen, target);
		if (point == null && itemSlot == null) {
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

		// The slot carries menu-relative coordinates, the same space the stored
		// fraction resolves into, so both land through the identical transform.
		// Saved fractions are authored against the preview's inclusive pixel range
		// (0..width - 1 / 0..height - 1). Use that same range at runtime so an
		// edge click cannot drift one logical pixel outside the inventory.
		double localX = itemSlot != null ? itemSlot.x + 8.0 : point.x() * Math.max(0, imageWidth - 1);
		double localY = itemSlot != null ? itemSlot.y + 8.0 : point.y() * Math.max(0, imageHeight - 1);
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

	private static boolean shouldPlaceCursor(final CursorTarget target) {
		// A stored point is Cursor Landing's own request for that target.
		if (customPoint(target) != null || landingItem(target) != null) {
			return true;
		}
		// Without one, only Center Mouse Fix asks for the vanilla centered position, and
		// only for the player inventory. The player inventory has no enable switch, so
		// this is the only thing that switch controls; reading it here is what makes it
		// mean anything at all.
		return isCenterMouseFixTarget(target);
	}

	/**
	 * The slot the player inventory landing should follow, when one is configured
	 * and the item is actually there.
	 *
	 * <p>A stored fraction is a position on the screen; during a fight the item is
	 * what the player is aiming for, and it moves. Resolving the slot at the moment
	 * the screen opens follows it. Only the hotbar and the main storage are
	 * searched: landing on the offhand would aim the swap at the item that is
	 * already in hand, and armor cannot be picked up by the swap either.</p>
	 */
	private static @Nullable Slot landingSlot(
		final Minecraft minecraft,
		final Screen screen,
		final CursorTarget target
	) {
		Item item = landingItem(target);
		if (item == null || minecraft.player == null || !(screen instanceof MenuAccess<?> access)) {
			return null;
		}
		for (Slot slot : access.getMenu().slots) {
			if (!slot.isActive()
				|| slot.container != minecraft.player.getInventory()
				|| slot.getContainerSlot() < 0
				|| slot.getContainerSlot() > LAST_MAIN_INVENTORY_SLOT
				|| slot.getItem().getItem() != item) {
				continue;
			}
			return slot;
		}
		return null;
	}

	private static @Nullable Item landingItem(final CursorTarget target) {
		InventoryTweaksConfig config = ConfigStore.get();
		if (target != CursorTarget.INVENTORY
			|| !isCustomCursorLandingAvailable()
			|| config.inventoryLandingItem == null) {
			return null;
		}
		Identifier identifier = Identifier.tryParse(config.inventoryLandingItem);
		return identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
	}

	private static @Nullable CursorPoint customPoint(final CursorTarget target) {
		InventoryTweaksConfig config = ConfigStore.get();
		return isCustomCursorLandingAvailable() && config.isCursorEnabled(target)
			? config.getPosition(target)
			: null;
	}

	private static boolean isCenterMouseFixTarget(final CursorTarget target) {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)
			&& target == CursorTarget.INVENTORY
			&& ConfigStore.get().centerMouseFix;
	}

	private static boolean isCustomCursorLandingAvailable() {
		return CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.CURSOR_LANDING);
	}

	private static boolean isScreenHandlingAvailable(final @Nullable Screen screen) {
		CursorTarget target = classify(screen);
		if (target == null) {
			return false;
		}
		return isCustomCursorLandingAvailable()
			|| (target == CursorTarget.INVENTORY
				&& CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS));
	}

	private static @Nullable CursorTarget classify(final @Nullable Screen screen) {
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
