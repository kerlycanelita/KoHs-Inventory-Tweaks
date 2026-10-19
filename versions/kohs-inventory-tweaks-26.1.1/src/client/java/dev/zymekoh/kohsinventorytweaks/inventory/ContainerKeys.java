package dev.zymekoh.kohsinventorytweaks.inventory;

import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityFeature;
import dev.zymekoh.kohsinventorytweaks.compat.CompatibilityIssueManager;
import dev.zymekoh.kohsinventorytweaks.config.ConfigStore;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractContainerScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.AbstractRecipeBookScreenAccessor;
import dev.zymekoh.kohsinventorytweaks.mixin.RecipeBookComponentAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * The swap, inventory and drop keys inside container screens, made to behave the same whatever
 * they are bound to and however long they are held.
 *
 * <p>A key held down keeps arriving as GLFW repeats, and a container screen handles each repeat
 * as a new press: a held offhand or hotbar key swaps the hovered slot back and forth some thirty
 * times a second, so where the item ends up depends on when the key comes up. Those repeats now do
 * nothing; each press swaps once. The drop key still repeats, as Vanilla drops one item per repeat
 * on purpose.</p>
 *
 * <p>Bound to a mouse button, the inventory key never closed a container screen and the drop key
 * never dropped from one, while the swap and hotbar keys always worked (MC-577). They now act as
 * their keyboard bindings do, through the same click types.</p>
 *
 * <p>Leave with movement keys, when chosen: a fresh press of a movement key in a container
 * screen closes it first, and the same press then moves the player as it would in the world.
 * The close goes out before the tick that sends the movement, the order a Vanilla player gets by
 * closing and then moving.</p>
 */
public final class ContainerKeys {
	private ContainerKeys() {
	}

	/** Whether this key event is a repeat of a swap key held in a container screen, to be ignored. */
	public static boolean ignoresRepeat(final Minecraft minecraft, final long window, final int action, final KeyEvent event) {
		if (action != GLFW.GLFW_REPEAT || minecraft == null || window != minecraft.getWindow().handle()
			|| !(minecraft.screen instanceof AbstractContainerScreen<?> screen)
			|| screen instanceof CreativeModeInventoryScreen
			|| typing(screen)
			|| !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return false;
		}
		Options options = minecraft.options;
		if (options.keySwapOffhand.matches(event)) {
			return true;
		}
		for (KeyMapping hotbar : options.keyHotbarSlots) {
			if (hotbar.matches(event)) {
				return true;
			}
		}
		return false;
	}

	/** Closes a container screen on a fresh movement key press, when Leave with movement keys is on. */
	public static void leaveWithMovement(final Minecraft minecraft, final long window, final int action, final KeyEvent event) {
		if (action != GLFW.GLFW_PRESS || minecraft == null || window != minecraft.getWindow().handle()
			|| !ConfigStore.get().leaveWithMovementKeys
			|| !(minecraft.screen instanceof AbstractContainerScreen<?> screen)
			|| screen instanceof CreativeModeInventoryScreen
			|| typing(screen)
			|| !CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)) {
			return;
		}
		Options options = minecraft.options;
		if (options.keyUp.matches(event) || options.keyDown.matches(event)
			|| options.keyLeft.matches(event) || options.keyRight.matches(event)) {
			screen.onClose();
		}
	}

	/** Whether a text field of this screen takes the keys: a rename box, a recipe search. */
	private static boolean typing(final AbstractContainerScreen<?> screen) {
		if (screen.getFocused() instanceof EditBox edit && edit.canConsumeInput()) {
			return true;
		}
		if (screen instanceof AbstractRecipeBookScreen<?> recipes) {
			var book = ((AbstractRecipeBookScreenAccessor) recipes).kohsInventoryTweaks$getRecipeBookComponent();
			EditBox search = ((RecipeBookComponentAccessor) book).kohsInventoryTweaks$getSearchBox();
			return book.isVisible() && search != null && search.canConsumeInput();
		}
		return false;
	}

	/**
	 * Handles the inventory and drop keys bound to a mouse button, where Vanilla only checks the
	 * swap and hotbar keys; true when the press was one of them.
	 */
	public static boolean mouseKeys(final AbstractContainerScreen<?> screen, final MouseButtonEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!CompatibilityIssueManager.isFeatureAvailable(CompatibilityFeature.INVENTORY_TWEAKS)
			|| screen instanceof CreativeModeInventoryScreen) {
			return false;
		}
		Options options = minecraft.options;
		if (options.keyInventory.matchesMouse(event)) {
			screen.onClose();
			return true;
		}
		if (!options.keyDrop.matchesMouse(event) || options.keySwapOffhand.matchesMouse(event)) {
			return false;
		}
		for (KeyMapping hotbar : options.keyHotbarSlots) {
			if (hotbar.matchesMouse(event)) {
				return false;
			}
		}
		// The slot under this press, as the swap and hotbar buttons resolve it.
		SlotTargeting.refresh(screen, event.x(), event.y(), false);
		AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
		Slot hovered = access.kohsInventoryTweaks$getHoveredSlot();
		if (hovered != null && hovered.hasItem()) {
			access.kohsInventoryTweaks$slotClicked(hovered, hovered.index, event.hasControlDown() ? 1 : 0, ContainerInput.THROW);
		}
		return true;
	}
}
