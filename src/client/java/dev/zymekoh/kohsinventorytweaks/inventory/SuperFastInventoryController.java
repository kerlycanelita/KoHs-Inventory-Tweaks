package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class SuperFastInventoryController {
	private static final long OPENING_COMBO_WINDOW_NANOS = 125_000_000L;
	private static long lastWorldOffhandPressNanos;
	private static @Nullable InventoryScreen openingInventory;
	private static boolean pendingInventoryOffhand;

	private SuperFastInventoryController() {
	}

	public static void onKeyboardEvent(
		final Minecraft minecraft,
		final long windowHandle,
		final int action,
		final KeyEvent event
	) {
		if (action != GLFW.GLFW_PRESS
			|| windowHandle != minecraft.getWindow().handle()
			|| minecraft.getOverlay() != null
			|| minecraft.player == null
			|| minecraft.gameMode == null) {
			return;
		}
		if (!ConfigStore.get().superFastInventory) {
			clearOpeningTransaction();
			return;
		}

		if (minecraft.screen instanceof InventoryScreen inventoryScreen) {
			if (inventoryScreen == openingInventory && minecraft.options.keySwapOffhand.matches(event)) {
				pendingInventoryOffhand = true;
			}
			return;
		}
		if (minecraft.screen != null) {
			return;
		}

		long now = System.nanoTime();
		if (minecraft.options.keySwapOffhand.matches(event)) {
			lastWorldOffhandPressNanos = now;
			return;
		}

		if (minecraft.options.keyInventory.matches(event)
			&& minecraft.options.keyInventory.consumeClick()) {
			if (minecraft.gameMode.isServerControlledInventory()) {
				clearOpeningTransaction();
				minecraft.player.sendOpenInventory();
				return;
			}

			boolean queuedOffhand = consumeRecentOffhandClick(minecraft, now);
			InventoryScreen inventoryScreen = new InventoryScreen(minecraft.player);
			openingInventory = inventoryScreen;
			pendingInventoryOffhand = queuedOffhand;
			minecraft.getTutorial().onOpenInventory();
			minecraft.setScreen(inventoryScreen);
		}
	}

	public static void onInventoryRendered(final Minecraft minecraft, final InventoryScreen inventoryScreen) {
		if (inventoryScreen != openingInventory) {
			return;
		}

		boolean shouldSwap = pendingInventoryOffhand;
		clearOpeningTransaction();
		if (!shouldSwap
			|| !ConfigStore.get().superFastInventory
			|| minecraft.screen != inventoryScreen
			|| minecraft.player == null
			|| minecraft.player.isSpectator()
			|| minecraft.gameMode == null
			|| !inventoryScreen.getMenu().getCarried().isEmpty()) {
			return;
		}

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) inventoryScreen;
		Slot hoveredSlot = accessor.kohsInventoryTweaks$getHoveredSlot();
		if (hoveredSlot != null && hoveredSlot.isActive()) {
			accessor.kohsInventoryTweaks$invokeSlotClicked(
				hoveredSlot,
				hoveredSlot.index,
				40,
				ContainerInput.SWAP
			);
		}
	}

	public static void onScreenRequested(final @Nullable Screen screen) {
		if (screen != openingInventory) {
			clearOpeningTransaction();
		}
		if (!(screen instanceof InventoryScreen)) {
			lastWorldOffhandPressNanos = 0L;
		}
	}

	private static boolean consumeRecentOffhandClick(final Minecraft minecraft, final long now) {
		boolean recent = lastWorldOffhandPressNanos != 0L
			&& now - lastWorldOffhandPressNanos <= OPENING_COMBO_WINDOW_NANOS;
		lastWorldOffhandPressNanos = 0L;
		if (!recent) {
			return false;
		}

		boolean consumed = false;
		while (minecraft.options.keySwapOffhand.consumeClick()) {
			consumed = true;
		}
		return consumed;
	}

	private static void clearOpeningTransaction() {
		openingInventory = null;
		pendingInventoryOffhand = false;
	}
}
