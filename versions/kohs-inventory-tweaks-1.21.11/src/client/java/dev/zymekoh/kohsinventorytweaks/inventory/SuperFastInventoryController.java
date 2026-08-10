package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class SuperFastInventoryController {
	private static final long OPENING_COMBO_WINDOW_NANOS = 125_000_000L;
	private static final long OPENING_TIMEOUT_NANOS = 750_000_000L;
	private static final long CLOSE_REOPEN_GUARD_NANOS = 45_000_000L;
	private static final long DUPLICATE_OPEN_GUARD_NANOS = 25_000_000L;
	private static final long SERVER_OPEN_GUARD_NANOS = 500_000_000L;
	private static long lastWorldOffhandPressNanos;
	private static long lastInventoryCloseNanos;
	private static long lastInventoryOpenRequestNanos;
	private static long openingStartedNanos;
	private static long serverOpenGuardUntilNanos;
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
				// The screen has not settled its hovered slot yet. Consume only this
				// queued key click so the normal world handler cannot swap the hotbar
				// before the first inventory render resolves the actual hovered slot.
				while (minecraft.options.keySwapOffhand.consumeClick()) {
					pendingInventoryOffhand = true;
				}
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

		if (minecraft.options.keyInventory.matches(event)) {
			boolean requested = false;
			while (minecraft.options.keyInventory.consumeClick()) {
				requested = true;
			}
			if (!requested
				|| (lastInventoryCloseNanos != 0L && now - lastInventoryCloseNanos <= CLOSE_REOPEN_GUARD_NANOS)
				|| (lastInventoryOpenRequestNanos != 0L && now - lastInventoryOpenRequestNanos <= DUPLICATE_OPEN_GUARD_NANOS)
				|| (serverOpenGuardUntilNanos != 0L && now < serverOpenGuardUntilNanos)
				|| openingInventory != null) {
				return;
			}
			lastInventoryOpenRequestNanos = now;
			if (minecraft.gameMode.isServerControlledInventory()) {
				clearOpeningTransaction();
				serverOpenGuardUntilNanos = now + SERVER_OPEN_GUARD_NANOS;
				minecraft.player.sendOpenInventory();
				return;
			}

			boolean queuedOffhand = consumeRecentOffhandClick(minecraft, now);
			InventoryScreen inventoryScreen = new InventoryScreen(minecraft.player);
			openingInventory = inventoryScreen;
			openingStartedNanos = now;
			pendingInventoryOffhand = queuedOffhand;
			minecraft.getTutorial().onOpenInventory();
			minecraft.setScreen(inventoryScreen);
		}
	}

	public static void onClientTick(final Minecraft minecraft) {
		if (openingInventory == null) {
			return;
		}
		if (!ConfigStore.get().superFastInventory
			|| minecraft.screen != openingInventory
			|| System.nanoTime() - openingStartedNanos > OPENING_TIMEOUT_NANOS) {
			clearOpeningTransaction();
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
				ClickType.SWAP
			);
		}
	}

	public static void onScreenRequested(final @Nullable Screen screen) {
		Screen current = Minecraft.getInstance().screen;
		if (current instanceof InventoryScreen && !(screen instanceof InventoryScreen)) {
			lastInventoryCloseNanos = System.nanoTime();
		}
		if (screen instanceof InventoryScreen) {
			serverOpenGuardUntilNanos = 0L;
		}
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
		openingStartedNanos = 0L;
	}
}
